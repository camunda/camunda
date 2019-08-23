/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
public class ElasticsearchManager {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchManager.class);

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private WorkflowIndex workflowType;

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

  private Script getUpdateScript() throws IOException {
    Map<String,Object> paramsMap = new HashMap<>();
    paramsMap.put("endDate", OffsetDateTime.now());
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


  public List<Long> queryWorkflowInstancesWithEmptyWorkflowVersion(Long workflowKey) {
    QueryBuilder queryBuilder = constantScoreQuery(
        joinWithAnd(
            termQuery(ListViewTemplate.WORKFLOW_KEY, workflowKey),
            boolQuery().mustNot(existsQuery(ListViewTemplate.WORKFLOW_VERSION))
        )
    );
    SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias())
        .source(new SearchSourceBuilder()
            .query(queryBuilder)
            .fetchSource(false));
    try {
      return ElasticsearchUtil.scrollKeysToList(searchRequest, esClient);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining workflow instance that has empty versions: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Gets the workflow by id.
   * @param workflowKey
   * @return
   */
  public WorkflowEntity getWorkflow(Long workflowKey) {
    final SearchRequest searchRequest = new SearchRequest(workflowType.getAlias())
        .source(new SearchSourceBuilder()
            .query(QueryBuilders.termQuery(WorkflowIndex.KEY, workflowKey)));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().totalHits == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().totalHits > 1) {
        throw new OperateRuntimeException(String.format("Could not find unique workflow with key '%s'.", workflowKey));
      } else {
        throw new OperateRuntimeException(String.format("Could not find workflow with key '%s'.", workflowKey));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the workflow: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private WorkflowEntity fromSearchHit(String workflowString) {
    return ElasticsearchUtil.fromSearchHit(workflowString, objectMapper, WorkflowEntity.class);
  }

}
