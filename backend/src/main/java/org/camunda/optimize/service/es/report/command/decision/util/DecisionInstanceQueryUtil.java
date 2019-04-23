/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.util;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.EVALUATION_DATE_TIME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class DecisionInstanceQueryUtil {
  private static final Logger logger = LoggerFactory.getLogger(DecisionInstanceQueryUtil.class);

  public static Optional<OffsetDateTime> getLatestEvaluationDate(final QueryBuilder baseQuery, final RestHighLevelClient esClient) {
    final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(baseQuery)
      .sort(SortBuilders.fieldSort(EVALUATION_DATE_TIME).order(SortOrder.DESC))
      .fetchSource(false)
      .size(1);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(DECISION_INSTANCE_TYPE))
        .types(DECISION_INSTANCE_TYPE)
        .source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      return Arrays.stream(response.getHits().getHits())
        .findFirst()
        .map(documentFields -> documentFields.field(EVALUATION_DATE_TIME))
        .map(startDateField -> (String) startDateField.getValue())
        .map(dateTimeFormatter::parse)
        .map(OffsetDateTime::from);
    } catch (IOException e) {
      logger.warn("Could retrieve evaluationDate of latest processInstance!");
    }

    return Optional.empty();
  }

}
