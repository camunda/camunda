/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.writer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.OperationReader;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.operation.BatchOperationRequestDto;
import org.camunda.operate.rest.dto.operation.OperationRequestDto;
import org.camunda.operate.rest.dto.operation.OperationResponseDto;
import org.camunda.operate.rest.exception.InvalidRequestException;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

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
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private OperationReader operationReader;

  /**
   * Finds operation, which are scheduled or locked with expired timeout, in the amount of configured batch size, and locks them.
   * @return map with workflow instance id as a key and list of locked operations as a value
   * @throws PersistenceException
   */
  public Map<String, List<OperationEntity>> lockBatch() throws PersistenceException {
    Map<String, List<OperationEntity>> result = new HashMap<>();

    final String workerId = operateProperties.getOperationExecutor().getWorkerId();
    final long lockTimeout = operateProperties.getOperationExecutor().getLockTimeout();
    final int batchSize = operateProperties.getOperationExecutor().getBatchSize();

    //select workflow instances, which has scheduled operations, or locked with expired lockExpirationTime
    final List<OperationEntity> operationEntities = operationReader.acquireOperations(batchSize);

    BulkRequestBuilder bulkRequest = esClient.prepareBulk();

    //lock the operations
    for (OperationEntity operation: operationEntities) {
      if (operation.getState().equals(OperationState.SCHEDULED) ||
        (operation.getState().equals(OperationState.LOCKED) && operation.getLockExpirationTime().isBefore(OffsetDateTime.now()))) {
        //lock operation: update workerId, state, lockExpirationTime
        operation.setState(OperationState.LOCKED);
        operation.setLockOwner(workerId);
        operation.setLockExpirationTime(OffsetDateTime.now().plus(lockTimeout, ChronoUnit.MILLIS));

        CollectionUtil.addToMap(result, operation.getWorkflowInstanceId(), operation);
        bulkRequest.add(createUpdateByIdRequest(operation, false));

      }
    }
    ElasticsearchUtil.processBulkRequest(bulkRequest, true);
    logger.debug("{} operations locked", result.entrySet().stream().mapToLong(e -> e.getValue().size()).sum());
    return result;
  }

  private UpdateRequestBuilder createUpdateByIdRequest(OperationEntity operation, boolean refreshImmediately) throws PersistenceException {
    try {

      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(operation), HashMap.class);

      UpdateRequestBuilder updateRequestBuilder = esClient
        .prepareUpdate(operationTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, operation.getId())
        .setDoc(jsonMap);
      if (refreshImmediately) {
        updateRequestBuilder = updateRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      }
      return updateRequestBuilder;
    } catch (IOException e) {
      logger.error("Error preparing the query to update operation", e);
      throw new PersistenceException(String.format("Error preparing the query to update operation [%s] for workflow instance id [%s]",
        operation.getId(), operation.getWorkflowInstanceId()), e);
    }

  }

  public void completeOperation(String workflowInstanceId, String incidentId, OperationType operationType) throws PersistenceException {
    try {
      Map<String, Object> params = new HashMap<>();
      params.put("endDate", OffsetDateTime.now());

      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(params), HashMap.class);

      String script =
            "ctx._source.state = '" + OperationState.COMPLETED.toString() + "';" +
            "ctx._source.endDate = params.endDate;" +
            "ctx._source.lockOwner = null;" +
            "ctx._source.lockExpirationTime = null;";
      Script updateScript = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, jsonMap);

      TermQueryBuilder incidentIdQ = null;
      if (incidentId != null) {
        incidentIdQ = termQuery(OperationTemplate.INCIDENT_ID, incidentId);
      }

      QueryBuilder query =
        joinWithAnd(
          termQuery(OperationTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId),
          incidentIdQ,
          termsQuery(OperationTemplate.STATE, OperationState.SENT.name(), OperationState.LOCKED.name()),
          termQuery(OperationTemplate.TYPE, operationType.name())
          );

      BulkByScrollResponse response =
        UpdateByQueryAction.INSTANCE.newRequestBuilder(esClient)
          .source(operationTemplate.getMainIndexName())
          .filter(query)
          .size(1)
          .script(updateScript)
          .refresh(true)
          .get();
      for (BulkItemResponse.Failure failure: response.getBulkFailures()) {
        logger.error(String.format("Complete operation failed for operation id [%s]: %s", failure.getId(),
          failure.getMessage()), failure.getCause());
        throw new PersistenceException("Complete operation failed: " + failure.getMessage(), failure.getCause());
      }

    } catch (IOException e) {
      logger.error("Error preparing the query to complete operation", e);
      throw new PersistenceException(String.format("Error preparing the query to complete operation of type [%s] for workflow instance id [%s]",
        operationType, workflowInstanceId), e);
    }
  }

  public void completeUpdateVariableOperation(String workflowInstanceId, String scopeId, String variableName) throws PersistenceException {
    try {
      Map<String, Object> params = new HashMap<>();
      params.put("endDate", OffsetDateTime.now());

      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(params), HashMap.class);

      String script =
            "ctx._source.state = '" + OperationState.COMPLETED.toString() + "';" +
            "ctx._source.endDate = params.endDate;" +
            "ctx._source.lockOwner = null;" +
            "ctx._source.lockExpirationTime = null;";
      Script updateScript = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, jsonMap);

      TermQueryBuilder scopeIdQ = termQuery(OperationTemplate.SCOPE_ID, scopeId);
      TermQueryBuilder variableNameIdQ = termQuery(OperationTemplate.VARIABLE_NAME, variableName);


      QueryBuilder query =
        joinWithAnd(
          termQuery(OperationTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId),
          scopeIdQ,
          variableNameIdQ,
          termsQuery(OperationTemplate.STATE, OperationState.SENT.name(), OperationState.LOCKED.name()),
          termQuery(OperationTemplate.TYPE, OperationType.UPDATE_VARIABLE.name())
          );

      BulkByScrollResponse response =
        UpdateByQueryAction.INSTANCE.newRequestBuilder(esClient)
          .source(operationTemplate.getMainIndexName())
          .filter(query)
          .size(1)
          .script(updateScript)
          .refresh(true)
          .get();
      for (BulkItemResponse.Failure failure: response.getBulkFailures()) {
        logger.error(String.format("Complete operation failed for operation id [%s]: %s", failure.getId(),
          failure.getMessage()), failure.getCause());
        throw new PersistenceException("Complete operation failed: " + failure.getMessage(), failure.getCause());
      }

    } catch (IOException e) {
      logger.error("Error preparing the query to complete operation", e);
      throw new PersistenceException(String.format("Error preparing the query to complete operation of type [%s] for workflow instance id [%s]",
        OperationType.UPDATE_VARIABLE, workflowInstanceId), e);
    }
  }

  public void updateOperation(OperationEntity operation) throws PersistenceException {
    final UpdateRequestBuilder updateRequest = createUpdateByIdRequest(operation, true);
    ElasticsearchUtil.executeUpdate(updateRequest);
  }

  /**
   * Schedule operation for one workflow instance.
   * @param workflowInstanceId
   * @param operationRequest
   * @return
   * @throws PersistenceException
   */
  public OperationResponseDto scheduleOperation(String workflowInstanceId, OperationRequestDto operationRequest) throws PersistenceException {
    logger.debug("Creating operation: workflowInstanceId [{}], operation type [{}]", workflowInstanceId, operationRequest.getOperationType());

    final List<IndexRequestBuilder> requests = new ArrayList<>();

    final OperationType operationType = operationRequest.getOperationType();
    if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
      final List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(workflowInstanceId);
      if (allIncidents.size() == 0) {
        //nothing to schedule
        return new OperationResponseDto(0, "No incidents found.");
      } else {
        for (IncidentEntity incident: allIncidents) {
          requests.add(getIndexOperationRequest(workflowInstanceId, incident.getId(), operationType));
        }
      }
    } else if (operationType.equals(OperationType.UPDATE_VARIABLE)) {
      requests.add(getIndexUpdateVariableOperationRequest(workflowInstanceId, operationRequest.getScopeId(), operationRequest.getName(), operationRequest.getValue()));
    } else {
      requests.add(getIndexOperationRequest(workflowInstanceId, operationRequest.getIncidentId(), operationType));
    }

    ElasticsearchUtil.executeIndex(esClient, requests);

    return new OperationResponseDto(requests.size());
  }

  /**
   * Schedule operations based of workflow instance queries.
   * @param batchOperationRequest
   * @return
   * @throws PersistenceException
   */
  public OperationResponseDto scheduleBatchOperation(BatchOperationRequestDto batchOperationRequest) throws PersistenceException {

    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final SearchRequestBuilder searchRequest = listViewReader.createSearchRequest(new ListViewRequestDto(batchOperationRequest.getQueries()));
    TimeValue keepAlive = new TimeValue(60000);

    SearchResponse scrollResp = searchRequest.setScroll(keepAlive).setSize(batchSize).get();

    SearchHits hits = scrollResp.getHits();
    validateTotalHits(hits);

    int operationsCount = 0;

    do {
      operationsCount += persistOperations(hits, batchOperationRequest);

      scrollResp = esClient.prepareSearchScroll(scrollResp.getScrollId()).setScroll(keepAlive).get();
      hits = scrollResp.getHits();

    } while (hits.getHits().length != 0);

    if (operationsCount == 0) {
      return new OperationResponseDto(0, "No operations were scheduled.");
    } else {
      return new OperationResponseDto(operationsCount);
    }

  }

  private int persistOperations(SearchHits hits, OperationRequestDto operationRequest) throws PersistenceException {
    final List<IndexRequestBuilder> requests = new ArrayList<>();
    for (SearchHit searchHit : hits.getHits()) {
      final OperationType operationType = operationRequest.getOperationType();
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
        final List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(searchHit.getId());
        if (allIncidents.size() != 0) {
          for (IncidentEntity incident: allIncidents) {
            requests.add(getIndexOperationRequest(searchHit.getId(), incident.getId(), operationType));
          }
        }
      } else {
        requests.add(getIndexOperationRequest(searchHit.getId(), operationRequest.getIncidentId(), operationType));
      }
    }
    ElasticsearchUtil.executeIndex(esClient, requests);
    return requests.size();
  }

  private IndexRequestBuilder getIndexUpdateVariableOperationRequest(String workflowInstanceId, String scopeId, String name, String value) throws PersistenceException {
    OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setWorkflowInstanceId(workflowInstanceId);
    operationEntity.setScopeId(scopeId);
    operationEntity.setVariableName(name);
    operationEntity.setVariableValue(value);
    operationEntity.setType(OperationType.UPDATE_VARIABLE);
    operationEntity.setStartDate(OffsetDateTime.now());
    operationEntity.setState(OperationState.SCHEDULED);

    try {
      return esClient
        .prepareIndex(operationTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, operationEntity.getId())
        .setSource(objectMapper.writeValueAsString(operationEntity), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to insert operation", e);
      throw new PersistenceException(String.format("Error preparing the query to insert operation [%s] for workflow instance id [%s]",
        OperationType.UPDATE_VARIABLE, workflowInstanceId), e);
    }
  }

  private IndexRequestBuilder getIndexOperationRequest(String workflowInstanceId, String incidentId, OperationType operationType) throws PersistenceException {
    OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setIncidentId(incidentId);
    operationEntity.setWorkflowInstanceId(workflowInstanceId);
    operationEntity.setType(operationType);
    operationEntity.setStartDate(OffsetDateTime.now());
    operationEntity.setState(OperationState.SCHEDULED);

    try {
      return esClient
        .prepareIndex(operationTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, operationEntity.getId())
        .setSource(objectMapper.writeValueAsString(operationEntity), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to insert operation", e);
      throw new PersistenceException(String.format("Error preparing the query to insert operation [%s] for workflow instance id [%s]",
        operationType, workflowInstanceId), e);
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
