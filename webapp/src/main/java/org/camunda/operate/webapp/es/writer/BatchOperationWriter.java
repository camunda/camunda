/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.es.writer;

import static org.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.entities.BatchOperationEntity;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.templates.BatchOperationTemplate;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.schema.templates.OperationTemplate;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.es.reader.ListViewReader;
import org.camunda.operate.webapp.es.reader.OperationReader;
import org.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import org.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.camunda.operate.webapp.security.UserService;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class BatchOperationWriter {

  private static final Logger logger = LoggerFactory.getLogger(BatchOperationWriter.class);

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private OperationReader operationReader;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private UserService userService;


  /**
   * Finds operation, which are scheduled or locked with expired timeout, in the amount of configured batch size, and locks them.
   * @return list of locked operations
   * @throws PersistenceException
   */
  public List<OperationEntity> lockBatch() throws PersistenceException {
    final String workerId = operateProperties.getOperationExecutor().getWorkerId();
    final long lockTimeout = operateProperties.getOperationExecutor().getLockTimeout();
    final int batchSize = operateProperties.getOperationExecutor().getBatchSize();

    //select workflow instances, which has scheduled operations, or locked with expired lockExpirationTime
    final List<OperationEntity> operationEntities = operationReader.acquireOperations(batchSize);

    BulkRequest bulkRequest = new BulkRequest();

    //lock the operations
    for (OperationEntity operation: operationEntities) {
      //lock operation: update workerId, state, lockExpirationTime
      operation.setState(OperationState.LOCKED);
      operation.setLockOwner(workerId);
      operation.setLockExpirationTime(OffsetDateTime.now().plus(lockTimeout, ChronoUnit.MILLIS));

      //TODO decide with index refresh
      bulkRequest.add(createUpdateByIdRequest(operation, false));
    }
    //TODO decide with index refresh
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, true);
    logger.debug("{} operations locked", operationEntities.size());
    return operationEntities;
  }

  private UpdateRequest createUpdateByIdRequest(OperationEntity operation, boolean refreshImmediately) throws PersistenceException {
    try {
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(operation), HashMap.class);

      UpdateRequest updateRequest = new UpdateRequest(operationTemplate.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, operation.getId())
          .doc(jsonMap);
      if (refreshImmediately) {
        updateRequest = updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      }
      return updateRequest;
    } catch (IOException e) {
      logger.error("Error preparing the query to update operation", e);
      throw new PersistenceException(String.format("Error preparing the query to update operation [%s] for workflow instance id [%s]",
          operation.getId(), operation.getWorkflowInstanceKey()), e);
    }
  }

  public void updateOperation(OperationEntity operation) throws PersistenceException {
    final UpdateRequest updateRequest = createUpdateByIdRequest(operation, true);
    ElasticsearchUtil.executeUpdate(esClient, updateRequest);
  }

  /**
   * Schedule operations based of workflow instance query.
   * @param batchOperationRequest
   * @return
   * @throws PersistenceException
   */
  public BatchOperationEntity scheduleBatchOperation(CreateBatchOperationRequestDto batchOperationRequest) {
    logger.debug("Creating batch operation: operationRequest [{}]", batchOperationRequest.toString());
    try {
      //create batch operation with unique id
      final BatchOperationEntity batchOperation = createBatchOperationEntity(batchOperationRequest.getOperationType(), batchOperationRequest.getName());

      //create single operations
      final int batchSize = operateProperties.getElasticsearch().getBatchSize();
      ConstantScoreQueryBuilder query = listViewReader.createWorkflowInstancesQuery(batchOperationRequest.getQuery());
      final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, ElasticsearchUtil.QueryType.ONLY_RUNTIME)
        .source(new SearchSourceBuilder().query(query).size(batchSize).fetchSource(false));
      AtomicInteger operationsCount = new AtomicInteger();
      ElasticsearchUtil.scrollWith(searchRequest, esClient,
          searchHits -> {
            try {
              final List<Long> workflowInstanceKeys = CollectionUtil.map(searchHits.getHits(),ElasticsearchUtil.searchHitIdToLong);
              operationsCount.addAndGet(persistOperations(workflowInstanceKeys, batchOperation.getId(), batchOperationRequest.getOperationType(), null));
            } catch (PersistenceException e) {
              throw new RuntimeException(e);
            }
          },
          null,
          searchHits -> {
            validateTotalHits(searchHits);
            batchOperation.setInstancesCount((int)searchHits.getTotalHits());
          });

      //update counts
      batchOperation.setOperationsTotalCount(operationsCount.get());

      if (operationsCount.get() == 0) {
        batchOperation.setEndDate(OffsetDateTime.now());
      }

      persistBatchOperationEntity(batchOperation);

      return batchOperation;

    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  /**
   * Schedule operation for single workflow instance.
   * @param workflowInstanceKey
   * @param operationRequest
   * @return
   * @throws PersistenceException
   */
  public BatchOperationEntity scheduleSingleOperation(long workflowInstanceKey, CreateOperationRequestDto operationRequest) {
    logger.debug("Creating operation: workflowInstanceKey [{}], operation type [{}]", workflowInstanceKey, operationRequest.getOperationType());
    try {
      //create batch operation with unique id
      final BatchOperationEntity batchOperation = createBatchOperationEntity(operationRequest.getOperationType(), operationRequest.getName());

      //create single operations
      BulkRequest bulkRequest = new BulkRequest();
      int operationsCount = 0;

      String noOperationsReason = null;

      final OperationType operationType = operationRequest.getOperationType();
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
        final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
        if (allIncidents.size() == 0) {
          //nothing to schedule
          //TODO delete batch operation entity
          batchOperation.setEndDate(OffsetDateTime.now());
          noOperationsReason = "No incidents found.";
        } else {
          for (IncidentEntity incident: allIncidents) {
            bulkRequest.add(getIndexOperationRequest(workflowInstanceKey, incident.getKey(), batchOperation.getId(), operationType));
            operationsCount++;
          }
        }
      } else if (operationType.equals(OperationType.UPDATE_VARIABLE)) {
        bulkRequest.add(
            getIndexUpdateVariableOperationRequest(workflowInstanceKey, ConversionUtils.toLongOrNull(operationRequest.getVariableScopeId()), operationRequest.getVariableName(),
                operationRequest.getVariableValue(), batchOperation.getId()));
        operationsCount++;
      } else {
        bulkRequest.add(getIndexOperationRequest(workflowInstanceKey, ConversionUtils.toLongOrNull(operationRequest.getIncidentId()), batchOperation.getId(), operationType));
        operationsCount++;
      }
      //update workflow instance
      bulkRequest.add(getUpdateWorkflowInstanceRequest(workflowInstanceKey, batchOperation.getId()));
      //update instances_count and operations_count of batch operation
      batchOperation.setOperationsTotalCount(operationsCount);
      batchOperation.setInstancesCount(1);
      //persist batch operation
      bulkRequest.add(getIndexBatchOperationRequest(batchOperation));

      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);

      return batchOperation;
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  private Script getUpdateBatchOperationIdScript(String batchOperationId) {
    Map<String,Object> paramsMap = new HashMap<>();
    paramsMap.put("batchOperationId", batchOperationId);

    String script = "if (ctx._source.batchOperationIds == null){"
        + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
        + "} else {"
        + "ctx._source.batchOperationIds.add(params.batchOperationId);"
        + "}";
    return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, paramsMap);
  }

  private BatchOperationEntity createBatchOperationEntity(OperationType operationType, String name) {
    BatchOperationEntity batchOperationEntity = new BatchOperationEntity();
    batchOperationEntity.generateId();
    batchOperationEntity.setType(operationType);
    batchOperationEntity.setName(name);
    batchOperationEntity.setStartDate(OffsetDateTime.now());
    batchOperationEntity.setUsername(userService.getCurrentUsername());
    return batchOperationEntity;
  }

  private String persistBatchOperationEntity(BatchOperationEntity batchOperationEntity) throws PersistenceException {
    try {
      IndexRequest indexRequest = getIndexBatchOperationRequest(batchOperationEntity);
      esClient.index(indexRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Error persisting batch operation", e);
      throw new PersistenceException(
          String.format("Error persisting batch operation of type [%s]", batchOperationEntity.getType()), e);
    }
    return batchOperationEntity.getId();
  }

  private IndexRequest getIndexBatchOperationRequest(BatchOperationEntity batchOperationEntity) throws JsonProcessingException {
    return new IndexRequest(batchOperationTemplate.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, batchOperationEntity.getId()).
            source(objectMapper.writeValueAsString(batchOperationEntity), XContentType.JSON);
  }

  private int persistOperations(List<Long> workflowInstanceKeys, String batchOperationId, OperationType operationType, String incidentId) throws PersistenceException {
    BulkRequest bulkRequest = new BulkRequest();
    int operationsCount = 0;

    Map<Long, List<Long>> incidentKeys = new HashMap<>();
    //prepare map of incident ids per workflow instance id
    if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
      incidentKeys = incidentReader.getIncidentKeysPerWorkflowInstance(workflowInstanceKeys);
    }

    for (Long wiId : workflowInstanceKeys) {
      //create single operations
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
        final List<Long> allIncidentKeys = incidentKeys.get(wiId);
        if (allIncidentKeys != null && allIncidentKeys.size() != 0) {
          for (Long incidentKey: allIncidentKeys) {
            bulkRequest.add(getIndexOperationRequest(wiId, incidentKey, batchOperationId, operationType));
            operationsCount++;
          }
        }
      } else {
        bulkRequest.add(getIndexOperationRequest(wiId, ConversionUtils.toLongOrNull(incidentId), batchOperationId, operationType));
        operationsCount++;
      }
      //update workflow instance
      bulkRequest.add(getUpdateWorkflowInstanceRequest(wiId, batchOperationId));
    }
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);
    return operationsCount;
  }

  private IndexRequest getIndexUpdateVariableOperationRequest(Long workflowInstanceKey, Long scopeKey, String name, String value, String batchOperationId) throws PersistenceException {
    OperationEntity operationEntity = createOperationEntity(workflowInstanceKey, OperationType.UPDATE_VARIABLE, batchOperationId);

    operationEntity.setScopeKey(scopeKey);
    operationEntity.setVariableName(name);
    operationEntity.setVariableValue(value);

    return createIndexRequest(operationEntity, OperationType.UPDATE_VARIABLE, workflowInstanceKey);
  }

  private IndexRequest getIndexOperationRequest(Long workflowInstanceKey, Long incidentKey, String batchOperationId, OperationType operationType) throws PersistenceException {
    OperationEntity operationEntity = createOperationEntity(workflowInstanceKey, operationType, batchOperationId);
    operationEntity.setIncidentKey(incidentKey);

    return createIndexRequest(operationEntity, operationType, workflowInstanceKey);
  }

  private UpdateRequest getUpdateWorkflowInstanceRequest(Long workflowInstanceKey, String batchOperationId) {
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.BATCH_OPERATION_IDS, batchOperationId);

      return new UpdateRequest(listViewTemplate.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, String.valueOf(workflowInstanceKey))
          .script(getUpdateBatchOperationIdScript(batchOperationId))
          .retryOnConflict(UPDATE_RETRY_COUNT);
  }

  private OperationEntity createOperationEntity(Long workflowInstanceKey, OperationType operationType, String batchOperationId) {
    OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setWorkflowInstanceKey(workflowInstanceKey);
    operationEntity.setType(operationType);
    operationEntity.setState(OperationState.SCHEDULED);
    operationEntity.setBatchOperationId(batchOperationId);
    operationEntity.setUsername(userService.getCurrentUsername());
    return operationEntity;
  }

  private IndexRequest createIndexRequest(OperationEntity operationEntity, OperationType operationType, Long workflowInstanceKey) throws PersistenceException {
    try {
      return new IndexRequest(operationTemplate.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, operationEntity.getId())
          .source(objectMapper.writeValueAsString(operationEntity), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to insert operation", e);
      throw new PersistenceException(
          String.format("Error preparing the query to insert operation [%s] for workflow instance id [%s]", operationType, workflowInstanceKey), e);
    }
  }

  private void validateTotalHits(SearchHits hits) {
    final long totalHits = hits.getTotalHits();
    if (operateProperties.getBatchOperationMaxSize() != null &&
        totalHits > operateProperties.getBatchOperationMaxSize()) {
      throw new InvalidRequestException(String
          .format("Too many workflow instances are selected for batch operation. Maximum possible amount: %s", operateProperties.getBatchOperationMaxSize()));
    }
  }

}
