/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
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

import static org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable
  .AbstractProcessInstanceDurationByVariableCommand.FILTERED_VARIABLES_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable
  .AbstractProcessInstanceDurationByVariableCommand.NESTED_AGGREGATION;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DATE_VARIABLES;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public class GroupByDateVariableIntervalSelection {
  private static final String STATS = "stats";
  public static final String VARIABLES_AGGREGATION = "variables";
  private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);


  public static Stats getMinMaxStats(QueryBuilder query, String esType, String field,
                                     RestHighLevelClient esClient,
                                     String nestedVariableNameFieldLabel, String variableName) {

    AggregationBuilder aggregationBuilder = nested(NESTED_AGGREGATION, DATE_VARIABLES).subAggregation(filter(
      FILTERED_VARIABLES_AGGREGATION,
      boolQuery()
        .must(
          termQuery(nestedVariableNameFieldLabel, variableName)
        )
    ));

    AggregationBuilder statsAgg = aggregationBuilder.subAggregation(AggregationBuilders
                                                                      .stats(STATS)
                                                                      .field(field)
                                                                      .format(OPTIMIZE_DATE_FORMAT));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(statsAgg)
      .size(0);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(esType))
        .types(esType)
        .source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not automatically determine date interval", e);
    }
    return ((Nested) response.getAggregations().get(NESTED_AGGREGATION)).getAggregations().get(STATS);
  }

  public static AggregationBuilder createDateVariableAggregation(String variableName,
                                                                 String nestedVariableNameFieldLabel,
                                                                 String nestedVariableValueFieldLabel,
                                                                 IntervalAggregationService intervalAggregationService,
                                                                 RestHighLevelClient esClient, QueryBuilder baseQuery) {
    Stats minMaxStats = getMinMaxStats(
      baseQuery,
      PROC_INSTANCE_TYPE,
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
        .dateHistogram(VARIABLES_AGGREGATION)
        .field(nestedVariableValueFieldLabel)
        .interval(1)
        .format(OPTIMIZE_DATE_FORMAT)
        .timeZone(DateTimeZone.getDefault());
    }
    return aggregationBuilder;
  }
}
