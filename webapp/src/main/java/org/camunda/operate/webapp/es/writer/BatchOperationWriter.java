/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.es.writer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.operate.entities.BatchOperationEntity;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.es.reader.ListViewReader;
import org.camunda.operate.webapp.es.reader.OperationReader;
import org.camunda.operate.es.schema.templates.BatchOperationTemplate;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.webapp.rest.dto.operation.OperationRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.OperationResponseDto;
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

      UpdateRequest updateRequest = new UpdateRequest(operationTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, operation.getId())
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
   * @param operationRequest
   * @return
   * @throws PersistenceException
   */
  public OperationResponseDto scheduleBatchOperation(OperationRequestDto operationRequest) {
    logger.debug("Creating batch operation: operationRequest [{}]", operationRequest.toString());
    try {
      //create batch operation with unique id
      final String batchOperationId = createAndPersistBatchOperationEntity(operationRequest);

      //create single operations
      final int batchSize = operateProperties.getElasticsearch().getBatchSize();
      ConstantScoreQueryBuilder query = listViewReader.createWorkflowInstancesQuery(operationRequest.getQuery());
      final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, ElasticsearchUtil.QueryType.ONLY_RUNTIME)
        .source(new SearchSourceBuilder().query(query).size(batchSize).fetchSource(false));
      AtomicInteger operationsCount = new AtomicInteger();
      ElasticsearchUtil.scrollWith(searchRequest, esClient,
          searchHits -> {
            try {
              final List<Long> workflowInstanceKeys = CollectionUtil.map(searchHits.getHits(),ElasticsearchUtil.searchHitIdToLong);
              operationsCount.addAndGet(persistOperations(workflowInstanceKeys, batchOperationId, operationRequest));
            } catch (PersistenceException e) {
              throw new RuntimeException(e);
            }
          },
          null,
          searchHits -> {
            validateTotalHits(searchHits);
          });

      if (operationsCount.get() == 0) {
        //TODO delete batch operation entity
        return new OperationResponseDto(batchOperationId, 0, "No operations were scheduled.");
      } else {
        return new OperationResponseDto(batchOperationId, operationsCount.get());
      }
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
  public OperationResponseDto scheduleSingleOperation(long workflowInstanceKey, OperationRequestDto operationRequest) {
    logger.debug("Creating operation: workflowInstanceKey [{}], operation type [{}]", workflowInstanceKey, operationRequest.getOperationType());
    try {
      //create batch operation with unique id
      final String batchOperationId = createAndPersistBatchOperationEntity(operationRequest);

      //create single operations
      BulkRequest bulkRequest = new BulkRequest();
      int operationsCount = 0;

      final OperationType operationType = operationRequest.getOperationType();
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
        final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
        if (allIncidents.size() == 0) {
          //nothing to schedule
          //TODO delete batch operation entity
          return new OperationResponseDto(batchOperationId, 0, "No incidents found.");
        } else {
          for (IncidentEntity incident: allIncidents) {
            bulkRequest.add(getIndexOperationRequest(workflowInstanceKey, incident.getKey(), batchOperationId, operationType));
            operationsCount++;
          }
        }
      } else if (operationType.equals(OperationType.UPDATE_VARIABLE)) {
        bulkRequest.add(
            getIndexUpdateVariableOperationRequest(workflowInstanceKey, ConversionUtils.toLongOrNull(operationRequest.getVariableScopeId()), operationRequest.getVariableName(),
                operationRequest.getVariableValue(), batchOperationId));
        operationsCount++;
      } else {
        bulkRequest.add(getIndexOperationRequest(workflowInstanceKey, ConversionUtils.toLongOrNull(operationRequest.getIncidentId()), batchOperationId, operationType));
        operationsCount++;
      }
      //update workflow instance
      bulkRequest.add(getUpdateWorkflowInstanceRequest(workflowInstanceKey, batchOperationId));
      //update instances_count and operations_count of batch operation
      bulkRequest.add(getUpdateBatchOperationCountsRequest(batchOperationId, operationsCount, 1));

      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);

      return new OperationResponseDto(batchOperationId, operationsCount);
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  private Script getUpdateBatchOperationIdScript(String batchOperationId) throws IOException {
    Map<String,Object> paramsMap = new HashMap<>();
    paramsMap.put("batchOperationId", batchOperationId);

    String script = "if (ctx._source.batchOperationId == null){"
        + "ctx._source.batchOperationId = new String[]{params.batchOperationId};"
        + "} else {"
        + "ctx._source.batchOperationId.add(params.batchOperationId);"
        + "}";
    return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, paramsMap);
  }


  private String createAndPersistBatchOperationEntity(OperationRequestDto operationRequest) throws PersistenceException {
    BatchOperationEntity batchOperationEntity = new BatchOperationEntity();
    batchOperationEntity.generateId();
    batchOperationEntity.setType(operationRequest.getOperationType());
    batchOperationEntity.setName(operationRequest.getName());
    batchOperationEntity.setStartDate(OffsetDateTime.now());
    batchOperationEntity.setUsername(userService.getCurrentUsername());
    try {
      IndexRequest indexRequest = new IndexRequest(batchOperationTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, batchOperationEntity.getId()).
          source(objectMapper.writeValueAsString(batchOperationEntity), XContentType.JSON)
          .setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
      esClient.index(indexRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Error persisting batch operation", e);
      throw new PersistenceException(
          String.format("Error persisting batch operation of type [%s]", operationRequest.getOperationType()), e);
    }
    return batchOperationEntity.getId();
  }

  private int persistOperations(List<Long> workflowInstanceKeys, String batchOperationId, OperationRequestDto operationRequest) throws PersistenceException {
    BulkRequest bulkRequest = new BulkRequest();
    final OperationType operationType = operationRequest.getOperationType();
    int operationsCount = 0;

    Map<Long, List<Long>> incidentKeys = new HashMap<>();
    //prepare map of incident ids per workflow instance id
    if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
      incidentKeys = incidentReader.getIncidentKeysPerWorkflowInstance(workflowInstanceKeys);
    }

    for (Long wiId : workflowInstanceKeys) {
      //create single operations
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
        final List<Long> allIncidentKeys = incidentKeys.get(wiId);
        if (allIncidentKeys != null && allIncidentKeys.size() != 0) {
          for (Long incidentKey: allIncidentKeys) {
            bulkRequest.add(getIndexOperationRequest(wiId, incidentKey, batchOperationId, operationType));
            operationsCount++;
          }
        }
      } else {
        bulkRequest.add(getIndexOperationRequest(wiId, ConversionUtils.toLongOrNull(operationRequest.getIncidentId()), batchOperationId, operationType));
        operationsCount++;
      }
      //update workflow instance
      bulkRequest.add(getUpdateWorkflowInstanceRequest(wiId, batchOperationId));
    }
    //update instances_count and operations_count of batch operation
    bulkRequest.add(getUpdateBatchOperationCountsRequest(batchOperationId, operationsCount, workflowInstanceKeys.size()));
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);
    return operationsCount;
  }

  private UpdateRequest getUpdateBatchOperationCountsRequest(String batchOperationId, int operationsCount, int instancesCount) {
    Map<String,Object> paramsMap = new HashMap<>();
    paramsMap.put("operationsCount", operationsCount);
    paramsMap.put("instancesCount", instancesCount);
    String scriptCode = "ctx._source." + BatchOperationTemplate.OPERATIONS_TOTAL_COUNT
        + " = ctx._source." + BatchOperationTemplate.OPERATIONS_TOTAL_COUNT + " + params.operationsCount;"
        + "ctx._source." + BatchOperationTemplate.INSTANCES_COUNT
        + " = ctx._source." + BatchOperationTemplate.INSTANCES_COUNT + " + params.instancesCount;";
    Script script = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, scriptCode, paramsMap);
    return new UpdateRequest(batchOperationTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, batchOperationId)
        .script(script).retryOnConflict(3);
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

  private UpdateRequest getUpdateWorkflowInstanceRequest(Long workflowInstanceKey, String batchOperationId) throws PersistenceException {
    try {
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.BATCH_OPERATION_ID, batchOperationId);

      return new UpdateRequest(listViewTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, String.valueOf(workflowInstanceKey))
          .script(getUpdateBatchOperationIdScript(batchOperationId));

    } catch (IOException e) {
      logger.error("Error preparing the query to update workflow instance with batchOperationId", e);
      throw new PersistenceException(
          String.format("Error preparing the query to update workflow instance [%s] with batchOperationId [%s]", workflowInstanceKey, batchOperationId), e);
    }
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
      return new IndexRequest(operationTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, operationEntity.getId())
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
