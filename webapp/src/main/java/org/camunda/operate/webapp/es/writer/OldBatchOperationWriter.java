/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.es.writer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.es.reader.ListViewReader;
import org.camunda.operate.webapp.es.reader.OperationReader;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.oldoperation.BatchOperationRequestDto;
import org.camunda.operate.webapp.rest.dto.oldoperation.OperationRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.OperationResponseDto;
import org.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.webapp.security.UserService;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
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

@Deprecated //OPE-786
@Component
public class OldBatchOperationWriter {

  private static final Logger logger = LoggerFactory.getLogger(OldBatchOperationWriter.class);

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
  private UserService userService;

  /**
   * Schedule operations based of workflow instance queries.
   * @param batchOperationRequest
   * @return
   * @throws PersistenceException
   */
  public OperationResponseDto scheduleBatchOperation(BatchOperationRequestDto batchOperationRequest) {

    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final SearchSourceBuilder searchSourceBuilder = listViewReader.createSearchSourceBuilder(new ListViewRequestDto(batchOperationRequest.getQueries()));
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, ElasticsearchUtil.QueryType.ONLY_RUNTIME)
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
        return new OperationResponseDto(null, 0, "No operations were scheduled.");
      } else {
        return new OperationResponseDto(null, operationsCount.get());
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
    operationEntity.setUsername(userService.getCurrentUsername());
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
