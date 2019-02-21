/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.entities.OperationState.LOCKED;
import static org.camunda.operate.entities.OperationState.SCHEDULED;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
public class OperationReader {

  private static final Logger logger = LoggerFactory.getLogger(OperationReader.class);

  private static final String SCHEDULED_OPERATION = SCHEDULED.toString();
  private static final String LOCKED_OPERATION = LOCKED.toString();

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

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

    final SearchRequestBuilder searchRequestBuilder = esClient.prepareSearch(operationTemplate.getAlias());
    searchRequestBuilder.setQuery(constantScoreQuery);

    SearchResponse response = searchRequestBuilder
      .setFrom(0)
      .setSize(batchSize)
      .get();

    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, OperationEntity.class);
  }

  public Map<String, List<OperationEntity>> getOperations(List<String> workflowInstanceIds) {
    Map<String, List<OperationEntity>> result = new HashMap<>();

    final ConstantScoreQueryBuilder query = constantScoreQuery(termsQuery(OperationTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceIds));

    final SearchRequestBuilder searchRequestBuilder = esClient.prepareSearch(operationTemplate.getAlias())
      .setQuery(query)
      .addSort(OperationTemplate.WORKFLOW_INSTANCE_ID, SortOrder.ASC)
      .addSort(OperationTemplate.START_DATE, SortOrder.DESC)
      .addSort(OperationTemplate.ID, SortOrder.ASC);
    TimeValue keepAlive = new TimeValue(2000);
    SearchResponse response = searchRequestBuilder
      .setScroll(keepAlive)
      .get();
    do {
      String scrollId = response.getScrollId();

      final List<OperationEntity> operationEntities = ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, OperationEntity.class);
      for (OperationEntity operationEntity: operationEntities) {
        CollectionUtil.addToMap(result, operationEntity.getWorkflowInstanceId(), operationEntity);
      }

      response = esClient
        .prepareSearchScroll(scrollId)
        .setScroll(keepAlive)
        .get();

    } while (response.getHits().getHits().length != 0);
    return result;
  }

  public List<OperationEntity> getOperations(String workflowInstanceId) {
    final ConstantScoreQueryBuilder query = constantScoreQuery(termQuery(OperationTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId));

    final SearchRequestBuilder searchRequestBuilder = esClient.prepareSearch(operationTemplate.getAlias())
      .setQuery(query)
      .addSort(OperationTemplate.START_DATE, SortOrder.DESC)
      .addSort(OperationTemplate.ID, SortOrder.ASC);

    return ElasticsearchUtil.scroll(searchRequestBuilder, OperationEntity.class, objectMapper, esClient);
  }

}
