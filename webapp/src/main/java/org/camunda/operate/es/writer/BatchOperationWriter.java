/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.writer;

import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.OperationReader;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.OperationRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.OperationResponseDto;
import org.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
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

        bulkRequest.add(createUpdateByIdRequest(operation, false));
    }
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

  public void completeOperation(Long workflowInstanceKey, Long incidentKey, OperationType operationType) throws PersistenceException {
    try {
      TermQueryBuilder incidentKeyQuery = null;
      if (incidentKey != null) {
        incidentKeyQuery = termQuery(OperationTemplate.INCIDENT_KEY, incidentKey);
      }

      QueryBuilder query =
        joinWithAnd(
          termQuery(OperationTemplate.WORKFLOW_INSTANCE_KEY, workflowInstanceKey),
          incidentKeyQuery,
          termsQuery(OperationTemplate.STATE, OperationState.SENT.name(), OperationState.LOCKED.name()),
          termQuery(OperationTemplate.TYPE, operationType.name())
        );

      executeUpdateQuery(query);
    } catch (IOException e) {
      logger.error("Error preparing the query to complete operation", e);
      throw new PersistenceException(String.format("Error preparing the query to complete operation of type [%s] for workflow instance id [%s]",
        operationType, workflowInstanceKey), e);
    }
  }

  public void completeUpdateVariableOperation(Long workflowInstanceKey, Long scopeKey, String variableName) throws PersistenceException {
    try {
      TermQueryBuilder scopeKeyQuery = termQuery(OperationTemplate.SCOPE_KEY, scopeKey);
      TermQueryBuilder variableNameIdQ = termQuery(OperationTemplate.VARIABLE_NAME, variableName);

      QueryBuilder query =
        joinWithAnd(
          termQuery(OperationTemplate.WORKFLOW_INSTANCE_KEY, workflowInstanceKey),
          scopeKeyQuery,
          variableNameIdQ,
          termsQuery(OperationTemplate.STATE, OperationState.SENT.name(), OperationState.LOCKED.name()),
          termQuery(OperationTemplate.TYPE, OperationType.UPDATE_VARIABLE.name())
        );

      executeUpdateQuery(query);
    } catch (IOException e) {
      logger.error("Error preparing the query to complete operation", e);
      throw new PersistenceException(String.format("Error preparing the query to complete operation of type [%s] for workflow instance id [%s]",
        OperationType.UPDATE_VARIABLE, workflowInstanceKey), e);
    }
  }

  public void updateOperation(OperationEntity operation) throws PersistenceException {
    final UpdateRequest updateRequest = createUpdateByIdRequest(operation, true);
    ElasticsearchUtil.executeUpdate(esClient, updateRequest);
  }

  /**
   * Schedule operation for one workflow instance.
   *
   * @param workflowInstanceKey
   * @param operationRequest
   * @return
   * @throws PersistenceException
   */
  public OperationResponseDto scheduleOperation(Long workflowInstanceKey, OperationRequestDto operationRequest) throws PersistenceException {
    logger.debug("Creating operation: workflowInstanceKey [{}], operation type [{}]", workflowInstanceKey, operationRequest.getOperationType());

    BulkRequest bulkRequest = new BulkRequest();

    final OperationType operationType = operationRequest.getOperationType();
    if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
      final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
      if (allIncidents.size() == 0) {
        //nothing to schedule
        return new OperationResponseDto(0, "No incidents found.");
      } else {
        for (IncidentEntity incident: allIncidents) {
          bulkRequest.add(getIndexOperationRequest(workflowInstanceKey, incident.getKey(), operationType));
        }
      }
    } else if (operationType.equals(OperationType.UPDATE_VARIABLE)) {
      bulkRequest.add(getIndexUpdateVariableOperationRequest(workflowInstanceKey, ConversionUtils.toLongOrNull(operationRequest.getScopeId()), operationRequest.getName(), operationRequest.getValue()));
    } else {
      bulkRequest.add(getIndexOperationRequest(workflowInstanceKey, ConversionUtils.toLongOrNull(operationRequest.getIncidentId()), operationType));
    }

    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);

    return new OperationResponseDto(bulkRequest.requests().size());
  }

  /**
   * Schedule operations based of workflow instance queries.
   * @param batchOperationRequest
   * @return
   * @throws PersistenceException
   */
  public OperationResponseDto scheduleBatchOperation(BatchOperationRequestDto batchOperationRequest) {

    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final SearchSourceBuilder searchSourceBuilder = listViewReader.createSearchSourceBuilder(new ListViewRequestDto(batchOperationRequest.getQueries()));
    final SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias())
        .source(searchSourceBuilder.size(batchSize).fetchSource(false));
    try {
      AtomicInteger operationsCount = new AtomicInteger();
      ElasticsearchUtil.scrollWith(searchRequest, esClient,
        searchHits -> {
          try {
            final List<Long> workflowInstanceKeys = CollectionUtil.map(searchHits.getHits(),ElasticsearchUtil.searchHitIdToLong);
            operationsCount.addAndGet(persistOperations(workflowInstanceKeys, batchOperationRequest));
          } catch (PersistenceException e) {
            throw new RuntimeException(e);
          }
        },
        null,
        searchHits -> {
          validateTotalHits(searchHits);
        });

      if (operationsCount.get() == 0) {
        return new OperationResponseDto(0, "No operations were scheduled.");
      } else {
        return new OperationResponseDto(operationsCount.get());
      }
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  private Script getUpdateScript() throws IOException {
    Map<String,Object> paramsMap = new HashMap<>();
    paramsMap.put("endDate",OffsetDateTime.now());
    Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(paramsMap), HashMap.class);

    String script =
          "ctx._source.state = '" + OperationState.COMPLETED.toString() + "';" +
          "ctx._source.endDate = params.endDate;" +
          "ctx._source.lockOwner = null;" +
          "ctx._source.lockExpirationTime = null;";
    return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, jsonMap);
  }

  private void executeUpdateQuery(QueryBuilder query) throws IOException, PersistenceException {
    UpdateByQueryRequest request = new UpdateByQueryRequest(operationTemplate.getMainIndexName())
        .setQuery(query)
        .setSize(1)
        .setScript(getUpdateScript())
        .setRefresh(true);

      final BulkByScrollResponse response = esClient.updateByQuery(request, RequestOptions.DEFAULT);
      for (BulkItemResponse.Failure failure: response.getBulkFailures()) {
        logger.error(String.format("Complete operation failed for operation id [%s]: %s", failure.getId(),
          failure.getMessage()), failure.getCause());
        throw new PersistenceException("Complete operation failed: " + failure.getMessage(), failure.getCause());
      }
  }

  private int persistOperations(List<Long> workflowInstanceKeys, OperationRequestDto operationRequest) throws PersistenceException {
    BulkRequest bulkRequest = new BulkRequest();
    final OperationType operationType = operationRequest.getOperationType();

    Map<Long, List<Long>> incidentKeys = new HashMap<>();
    //prepare map of incident ids per workflow instance id
    if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
      incidentKeys = incidentReader.getIncidentKeysPerWorkflowInstance(workflowInstanceKeys);
    }

    for (Long wiId : workflowInstanceKeys) {
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
        final List<Long> allIncidentKeys = incidentKeys.get(wiId);
        if (allIncidentKeys != null && allIncidentKeys.size() != 0) {
          for (Long incidentKey: allIncidentKeys) {
            bulkRequest.add(getIndexOperationRequest(wiId, incidentKey, operationType));
          }
        }
      } else {
        bulkRequest.add(getIndexOperationRequest(wiId, ConversionUtils.toLongOrNull(operationRequest.getIncidentId()), operationType));
      }
    }
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);
    return bulkRequest.requests().size();
  }

  private IndexRequest getIndexUpdateVariableOperationRequest(Long workflowInstanceKey, Long scopeKey, String name, String value) throws PersistenceException {
    OperationEntity operationEntity = createOperationEntity(workflowInstanceKey, OperationType.UPDATE_VARIABLE);

    operationEntity.setScopeKey(scopeKey);
    operationEntity.setVariableName(name);
    operationEntity.setVariableValue(value);

    return createIndexRequest(operationEntity, OperationType.UPDATE_VARIABLE, workflowInstanceKey);
  }

  private IndexRequest getIndexOperationRequest(Long workflowInstanceKey, Long incidentKey, OperationType operationType) throws PersistenceException {
    OperationEntity operationEntity = createOperationEntity(workflowInstanceKey, operationType);

    operationEntity.setIncidentKey(incidentKey);

    return createIndexRequest(operationEntity, operationType, workflowInstanceKey);
  }

  private OperationEntity createOperationEntity(Long workflowInstanceKey, OperationType operationType) {
    OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setWorkflowInstanceKey(workflowInstanceKey);
    operationEntity.setType(operationType);
    operationEntity.setStartDate(OffsetDateTime.now());
    operationEntity.setState(OperationState.SCHEDULED);
    return operationEntity;
  }

  private IndexRequest createIndexRequest(OperationEntity operationEntity,OperationType operationType,Long workflowInstanceKey) throws PersistenceException {
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
