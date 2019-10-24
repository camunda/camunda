/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.util;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.EVALUATION_DATE_TIME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Slf4j
public class DecisionInstanceQueryUtil {

  public static Optional<OffsetDateTime> getLatestEvaluationDate(final QueryBuilder baseQuery,
                                                                 final OptimizeElasticsearchClient esClient) {
    final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(baseQuery)
      .sort(SortBuilders.fieldSort(EVALUATION_DATE_TIME).order(SortOrder.DESC))
      .fetchSource(false)
      .size(1);
    SearchRequest searchRequest = new SearchRequest(DECISION_INSTANCE_INDEX_NAME).source(searchSourceBuilder);

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
      log.warn("Could retrieve evaluationDate of latest processInstance!");
    }

    return Optional.empty();
  }

}
