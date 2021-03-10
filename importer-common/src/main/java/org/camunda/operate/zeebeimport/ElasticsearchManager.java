/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.schema.indices.WorkflowIndex;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.util.OperationsManager;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
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

  @Autowired
  private OperationsManager operationsManager;

  public void completeOperation(Long zeebeCommandKey, Long workflowInstanceKey, Long incidentKey, OperationType operationType, BulkRequest bulkRequest)
      throws PersistenceException {
    OperationEntity operation = getOperation(zeebeCommandKey, workflowInstanceKey, incidentKey, operationType);
    if (operation != null) {
      if (operation.getBatchOperationId() != null) {
        operationsManager.updateFinishedInBatchOperation(operation.getBatchOperationId(), bulkRequest);
      }
      completeOperation(operation.getId(), bulkRequest);
    }
  }

  public OperationEntity getOperation(Long zeebeCommandKey, Long workflowInstanceKey, Long incidentKey, OperationType operationType){
    if (workflowInstanceKey == null && zeebeCommandKey == null) {
      throw new OperateRuntimeException("Wrong call to search for operation. Not enough parameters.");
    }
    TermQueryBuilder zeebeCommandKeyQ = zeebeCommandKey != null ? termQuery(OperationTemplate.ZEEBE_COMMAND_KEY, zeebeCommandKey) : null;
    TermQueryBuilder workflowInstanceKeyQ = workflowInstanceKey != null ? termQuery(OperationTemplate.WORKFLOW_INSTANCE_KEY, workflowInstanceKey) : null;
    TermQueryBuilder incidentKeyQ = incidentKey != null ? termQuery(OperationTemplate.INCIDENT_KEY, incidentKey) : null;
    TermQueryBuilder operationTypeQ = zeebeCommandKey != null ? termQuery(OperationTemplate.TYPE, operationType.name()) : null;

    QueryBuilder query =
        joinWithAnd(
            zeebeCommandKeyQ,
            workflowInstanceKeyQ,
            incidentKeyQ,
            operationTypeQ,
            termsQuery(OperationTemplate.STATE, OperationState.SENT.name(), OperationState.LOCKED.name())
        );
    final SearchRequest searchRequest = new SearchRequest(operationTemplate.getAlias())
        .source(new SearchSourceBuilder()
            .query(query)
            .size(1));
    try {
      SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().totalHits == 1) {
        return ElasticsearchUtil.fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, OperationEntity.class);
      } else if (response.getHits().totalHits > 1) {
        throw new OperateRuntimeException(String
            .format("Could not find unique operation for parameters zeebeCommandKey [%d], workflowInstanceKey [%d], incidentKey [%d], operationType [%s].",
                zeebeCommandKey, workflowInstanceKey, incidentKey, operationType.name()));
      } else {
        return null;
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the operation: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  public void completeOperation(String operationId, BulkRequest bulkRequest) {
    UpdateRequest updateRequest = new UpdateRequest(operationTemplate.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, operationId)
        .script(getUpdateOperationScript())
        .retryOnConflict(UPDATE_RETRY_COUNT);
    bulkRequest.add(updateRequest);
  }

  private Script getUpdateOperationScript() {
    String script =
        "ctx._source.state = '" + OperationState.COMPLETED.toString() + "';" +
            "ctx._source.lockOwner = null;" +
            "ctx._source.lockExpirationTime = null;";
    return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, Collections.emptyMap());
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
