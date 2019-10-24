/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.joda.time.DateTimeZone;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.stats;

public class GroupByDateVariableIntervalSelection {
  private static final String STATS = "stats";
  private static final String NESTED_AGGREGATION = "nested";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  public static AggregationBuilder createDateVariableAggregation(String aggregationName,
                                                                 String variableName,
                                                                 String nestedVariableNameFieldLabel,
                                                                 String nestedVariableValueFieldLabel,
                                                                 String esType,
                                                                 String nestedPath,
                                                                 IntervalAggregationService intervalAggregationService,
                                                                 OptimizeElasticsearchClient esClient,
                                                                 QueryBuilder baseQuery) {
    Stats minMaxStats = getMinMaxStats(
      baseQuery,
      esType,
      nestedPath,
      nestedVariableValueFieldLabel,
      esClient,
      nestedVariableNameFieldLabel,
      variableName
    );

    Optional<AggregationBuilder> optionalAgg = intervalAggregationService
      .createIntervalAggregationFromGivenRange(
        nestedVariableValueFieldLabel,
        OffsetDateTime.parse(minMaxStats.getMinAsString(), dateTimeFormatter),
        OffsetDateTime.parse(minMaxStats.getMaxAsString(), dateTimeFormatter)
      );
    AggregationBuilder aggregationBuilder;
    if (optionalAgg.isPresent() && !((RangeAggregationBuilder) optionalAgg.get()).ranges().isEmpty()) {
      aggregationBuilder = optionalAgg.get();
    } else {
      aggregationBuilder = AggregationBuilders
        .dateHistogram(aggregationName)
        .field(nestedVariableValueFieldLabel)
        .interval(1)
        .format(OPTIMIZE_DATE_FORMAT)
        .timeZone(DateTimeZone.getDefault());
    }
    return aggregationBuilder;
  }

  private static Stats getMinMaxStats(QueryBuilder query,
                                      String indexName,
                                      String nestedPath,
                                      String field,
                                      OptimizeElasticsearchClient esClient,
                                      String nestedVariableNameFieldLabel,
                                      String variableName) {

    AggregationBuilder aggregationBuilder = nested(NESTED_AGGREGATION, nestedPath).subAggregation(filter(
      FILTERED_VARIABLES_AGGREGATION,
      boolQuery()
        .must(
          termQuery(nestedVariableNameFieldLabel, variableName)
        )
    ));

    AggregationBuilder statsAgg = aggregationBuilder
      .subAggregation(
        stats(STATS)
          .field(field)
          .format(OPTIMIZE_DATE_FORMAT)
      );

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(statsAgg)
      .size(0);
    SearchRequest searchRequest = new SearchRequest(indexName).source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not automatically determine date interval", e);
    }
    return ((Nested) response.getAggregations().get(NESTED_AGGREGATION)).getAggregations().get(STATS);
  }
}
