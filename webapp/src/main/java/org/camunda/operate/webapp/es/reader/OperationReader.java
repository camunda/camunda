/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.es.reader;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.BatchOperationEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.schema.templates.BatchOperationTemplate;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.webapp.security.UserService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.camunda.operate.entities.OperationState.LOCKED;
import static org.camunda.operate.entities.OperationState.SCHEDULED;
import static org.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static org.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
public class OperationReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(OperationReader.class);

  private static final String SCHEDULED_OPERATION = SCHEDULED.toString();
  private static final String LOCKED_OPERATION = LOCKED.toString();

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  private UserService userService;

  /**
   * Request workflow instances, that have scheduled operations or locked but with expired locks.
   * @param batchSize
   * @return
   */
  public List<OperationEntity> acquireOperations(int batchSize) {
    final TermQueryBuilder scheduledOperationsQuery = termQuery(OperationTemplate.STATE, SCHEDULED_OPERATION);
    final TermQueryBuilder lockedOperationsQuery = termQuery(OperationTemplate.STATE, LOCKED_OPERATION);
    final RangeQueryBuilder lockExpirationTimeQuery = rangeQuery(OperationTemplate.LOCK_EXPIRATION_TIME);
    lockExpirationTimeQuery.lte(dateTimeFormatter.format(OffsetDateTime.now()));

    final QueryBuilder operationsQuery = joinWithOr(scheduledOperationsQuery, joinWithAnd(lockedOperationsQuery, lockExpirationTimeQuery));

    ConstantScoreQueryBuilder constantScoreQuery = constantScoreQuery(operationsQuery);

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(operationTemplate, ONLY_RUNTIME)
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery)
        .sort(OperationTemplate.START_DATE, SortOrder.ASC)
        .from(0)
        .size(batchSize));
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return ElasticsearchUtil.mapSearchHits(searchResponse.getHits().getHits(), objectMapper, OperationEntity.class);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while acquiring operations for execution: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public Map<Long, List<OperationEntity>> getOperationsPerWorkflowInstanceKey(List<Long> workflowInstanceKeys) {
    Map<Long, List<OperationEntity>> result = new HashMap<>();

    final ConstantScoreQueryBuilder query = constantScoreQuery(termsQuery(OperationTemplate.WORKFLOW_INSTANCE_KEY, workflowInstanceKeys));

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(operationTemplate, ElasticsearchUtil.QueryType.ALL)
      .source(new SearchSourceBuilder()
        .query(query)
        .sort(OperationTemplate.WORKFLOW_INSTANCE_KEY, SortOrder.ASC)
        .sort(OperationTemplate.START_DATE, SortOrder.DESC)
        .sort(OperationTemplate.ID, SortOrder.ASC));

    try {
      ElasticsearchUtil.scroll(searchRequest, OperationEntity.class, objectMapper, esClient, hits -> {
        final List<OperationEntity> operationEntities = ElasticsearchUtil.mapSearchHits(hits.getHits(), objectMapper, OperationEntity.class);
        for (OperationEntity operationEntity: operationEntities) {
          CollectionUtil.addToMap(result, operationEntity.getWorkflowInstanceKey(), operationEntity);
        }
      }, null);

      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining operations per workflow instance id: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public Map<Long, List<OperationEntity>> getOperationsPerIncidentKey(Long workflowInstanceKey) {
    Map<Long, List<OperationEntity>> result = new HashMap<>();

    final ConstantScoreQueryBuilder query = constantScoreQuery(termQuery(OperationTemplate.WORKFLOW_INSTANCE_KEY, workflowInstanceKey));

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(operationTemplate, ONLY_RUNTIME)
      .source(new SearchSourceBuilder()
        .query(query)
        .sort(OperationTemplate.INCIDENT_KEY, SortOrder.ASC)
        .sort(OperationTemplate.START_DATE, SortOrder.DESC)
        .sort(OperationTemplate.ID, SortOrder.ASC));
    try {
      ElasticsearchUtil.scroll(searchRequest, OperationEntity.class, objectMapper, esClient, hits -> {
        final List<OperationEntity> operationEntities = ElasticsearchUtil.mapSearchHits(hits.getHits(), objectMapper, OperationEntity.class);
        for (OperationEntity operationEntity: operationEntities) {
          CollectionUtil.addToMap(result, operationEntity.getIncidentKey(), operationEntity);
        }
      }, null);
      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining operations per incident id: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }

  }

  public Map<String, List<OperationEntity>> getOperationsPerVariableName(Long workflowInstanceKey, Long scopeKey) {
    Map<String, List<OperationEntity>> result = new HashMap<>();

    final TermQueryBuilder workflowInstanceKeyQuery = termQuery(OperationTemplate.WORKFLOW_INSTANCE_KEY, workflowInstanceKey);
    final TermQueryBuilder scopeKeyQuery = termQuery(OperationTemplate.SCOPE_KEY, scopeKey);
    final TermQueryBuilder operationTypeQ = termQuery(OperationTemplate.TYPE, OperationType.UPDATE_VARIABLE.name());
    final ConstantScoreQueryBuilder query = constantScoreQuery(joinWithAnd(workflowInstanceKeyQuery, scopeKeyQuery, operationTypeQ));

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(operationTemplate, ALL)
      .source(new SearchSourceBuilder()
        .query(query)
        .sort(OperationTemplate.START_DATE, SortOrder.DESC)
        .sort(OperationTemplate.ID, SortOrder.ASC));
    try {
      ElasticsearchUtil.scroll(searchRequest, OperationEntity.class, objectMapper, esClient, hits -> {
        final List<OperationEntity> operationEntities = ElasticsearchUtil.mapSearchHits(hits.getHits(), objectMapper, OperationEntity.class);
        for (OperationEntity operationEntity: operationEntities) {
          CollectionUtil.addToMap(result, operationEntity.getVariableName(), operationEntity);
        }
      }, null);
      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining operations per variable name: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }

  }

  public List<OperationEntity> getOperationsByWorkflowInstanceKey(Long workflowInstanceKey) {

    final ConstantScoreQueryBuilder query;
    if (workflowInstanceKey == null) {
      query = constantScoreQuery(matchAllQuery());
    } else {
      query = constantScoreQuery(termQuery(OperationTemplate.WORKFLOW_INSTANCE_KEY, workflowInstanceKey));
    }
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(operationTemplate, ALL)
      .source(new SearchSourceBuilder()
        .query(query)
        .sort(OperationTemplate.START_DATE, SortOrder.DESC)
        .sort(OperationTemplate.ID, SortOrder.ASC));
    try {
      return ElasticsearchUtil.scroll(searchRequest, OperationEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining operations: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  //this query will be extended
  public List<BatchOperationEntity> getBatchOperations(int pageSize){
    String username = userService.getCurrentUsername();
    TermsQueryBuilder isOfCurrentUser = termsQuery(BatchOperationTemplate.USERNAME, username);
    SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(batchOperationTemplate, ALL)
        .source(new SearchSourceBuilder()
            .query(constantScoreQuery(isOfCurrentUser))
            .size(pageSize));
    try {
      return ElasticsearchUtil
          .mapSearchHits(esClient.search(searchRequest, RequestOptions.DEFAULT).getHits().getHits(), objectMapper, BatchOperationEntity.class);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining batch operations: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }

  }

}
