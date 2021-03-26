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
import org.camunda.operate.entities.ProcessEntity;
import org.camunda.operate.schema.indices.ProcessIndex;
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
  private ProcessIndex processType;

  @Autowired
  private OperationsManager operationsManager;

  public void completeOperation(Long zeebeCommandKey, Long processInstanceKey, Long incidentKey, OperationType operationType, BulkRequest bulkRequest)
      throws PersistenceException {
    OperationEntity operation = getOperation(zeebeCommandKey, processInstanceKey, incidentKey, operationType);
    if (operation != null) {
      if (operation.getBatchOperationId() != null) {
        operationsManager.updateFinishedInBatchOperation(operation.getBatchOperationId(), bulkRequest);
      }
      completeOperation(operation.getId(), bulkRequest);
    }
  }

  public OperationEntity getOperation(Long zeebeCommandKey, Long processInstanceKey, Long incidentKey, OperationType operationType){
    if (processInstanceKey == null && zeebeCommandKey == null) {
      throw new OperateRuntimeException("Wrong call to search for operation. Not enough parameters.");
    }
    TermQueryBuilder zeebeCommandKeyQ = zeebeCommandKey != null ? termQuery(OperationTemplate.ZEEBE_COMMAND_KEY, zeebeCommandKey) : null;
    TermQueryBuilder processInstanceKeyQ = processInstanceKey != null ? termQuery(OperationTemplate.PROCESS_INSTANCE_KEY, processInstanceKey) : null;
    TermQueryBuilder incidentKeyQ = incidentKey != null ? termQuery(OperationTemplate.INCIDENT_KEY, incidentKey) : null;
    TermQueryBuilder operationTypeQ = zeebeCommandKey != null ? termQuery(OperationTemplate.TYPE, operationType.name()) : null;

    QueryBuilder query =
        joinWithAnd(
            zeebeCommandKeyQ,
            processInstanceKeyQ,
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
            .format("Could not find unique operation for parameters zeebeCommandKey [%d], processInstanceKey [%d], incidentKey [%d], operationType [%s].",
                zeebeCommandKey, processInstanceKey, incidentKey, operationType.name()));
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

  public List<Long> queryProcessInstancesWithEmptyProcessVersion(Long processDefinitionKey) {
    QueryBuilder queryBuilder = constantScoreQuery(
        joinWithAnd(
            termQuery(ListViewTemplate.PROCESS_KEY, processDefinitionKey),
            boolQuery().mustNot(existsQuery(ListViewTemplate.PROCESS_VERSION))
        )
    );
    SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias())
        .source(new SearchSourceBuilder()
            .query(queryBuilder)
            .fetchSource(false));
    try {
      return ElasticsearchUtil.scrollKeysToList(searchRequest, esClient);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining process instance that has empty versions: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Gets the process by id.
   * @param processDefinitionKey
   * @return
   */
  public ProcessEntity getProcess(Long processDefinitionKey) {
    final SearchRequest searchRequest = new SearchRequest(processType.getAlias())
        .source(new SearchSourceBuilder()
            .query(QueryBuilders.termQuery(ProcessIndex.KEY, processDefinitionKey)));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().totalHits == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().totalHits > 1) {
        throw new OperateRuntimeException(String.format("Could not find unique process with key '%s'.", processDefinitionKey));
      } else {
        throw new OperateRuntimeException(String.format("Could not find process with key '%s'.", processDefinitionKey));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private ProcessEntity fromSearchHit(String processString) {
    return ElasticsearchUtil.fromSearchHit(processString, objectMapper, ProcessEntity.class);
  }

}
