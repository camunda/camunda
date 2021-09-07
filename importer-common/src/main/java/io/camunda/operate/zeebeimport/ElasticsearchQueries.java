/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.util.ElasticsearchUtil.QueryType;
import java.io.IOException;
import java.util.List;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
public class ElasticsearchQueries {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchQueries.class);

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

  public List<OperationEntity> getOperations(Long zeebeCommandKey, Long processInstanceKey, Long incidentKey, OperationType operationType){
    if (processInstanceKey == null && zeebeCommandKey == null) {
      throw new OperateRuntimeException("Wrong call to search for operation. Not enough parameters.");
    }
    TermQueryBuilder zeebeCommandKeyQ = zeebeCommandKey != null ? termQuery(OperationTemplate.ZEEBE_COMMAND_KEY, zeebeCommandKey) : null;
    TermQueryBuilder processInstanceKeyQ = processInstanceKey != null ? termQuery(OperationTemplate.PROCESS_INSTANCE_KEY, processInstanceKey) : null;
    TermQueryBuilder incidentKeyQ = incidentKey != null ? termQuery(OperationTemplate.INCIDENT_KEY, incidentKey) : null;
    TermQueryBuilder operationTypeQ = operationType != null ? termQuery(OperationTemplate.TYPE, operationType.name()) : null;

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
      return scroll(searchRequest, OperationEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the operations: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
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
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().getTotalHits().value > 1) {
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

  public String findProcessInstanceTreePath(final long parentProcessInstanceKey) {
    final SearchRequest searchRequest = ElasticsearchUtil
        .createSearchRequest(listViewTemplate, QueryType.ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .query(termQuery(ListViewTemplate.KEY, parentProcessInstanceKey))
            .fetchSource(ListViewTemplate.TREE_PATH, null));
    try {
      final SearchHits hits = esClient.search(searchRequest, RequestOptions.DEFAULT).getHits();
      if (hits.getTotalHits().value > 0) {
        return (String) hits.getHits()[0].getSourceAsMap().get(ListViewTemplate.TREE_PATH);
      }
      return null;
    } catch (IOException e) {
      final String message = String
          .format("Exception occurred, while searching for parent process instance processes: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private ProcessEntity fromSearchHit(String processString) {
    return ElasticsearchUtil.fromSearchHit(processString, objectMapper, ProcessEntity.class);
  }

}
