/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.Stats;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@RequiredArgsConstructor
@Component
@Slf4j
public class MinMaxStatsService {
  private static final String NESTED_AGGREGATION_FIRST_FIELD = "nestedAggField1";
  private static final String NESTED_AGGREGATION_SECOND_FIELD = "nestedAggField2";

  private static final String FILTER_AGGREGATION_FIRST_FIELD = "filterAggField1";
  private static final String FILTER_AGGREGATION_SECOND_FIELD = "filterAggField2";

  private static final String STATS_AGGREGATION_FIRST_FIELD = "statsAggField1";
  private static final String STATS_AGGREGATION_SECOND_FIELD = "statsAggField2";

  private final DateTimeFormatter dateTimeFormatter;
  private final OptimizeElasticsearchClient esClient;

  public MinMaxStatDto getMinMaxDateRange(final ExecutionContext<? extends SingleReportDataDto> context,
                                          final QueryBuilder query,
                                          final String indexName,
                                          final String field) {
    return getMinMaxDateRangeForNestedField(context, query, indexName, field, field, null, null);
  }

  public MinMaxStatDto getMinMaxDateRangeForCrossField(final ExecutionContext<? extends SingleReportDataDto> context,
                                                       final QueryBuilder query,
                                                       final String indexName,
                                                       final String firstField,
                                                       final String secondField) {
    return getMinMaxDateRangeForNestedField(context, query, indexName, firstField, secondField, null, null);
  }

  public MinMaxStatDto getMinMaxDateRangeForNestedField(final ExecutionContext<? extends SingleReportDataDto> context,
                                                        final QueryBuilder query,
                                                        final String indexName,
                                                        final String field,
                                                        final String pathForNestedStatsAgg,
                                                        final BoolQueryBuilder filterQueryToWrapStatsWith) {
    return getMinMaxDateRangeForNestedField(
      context, query, indexName, field, field, pathForNestedStatsAgg, filterQueryToWrapStatsWith
    );
  }

  public MinMaxStatDto getMinMaxDateRangeForNestedField(final ExecutionContext<? extends SingleReportDataDto> context,
                                                        final QueryBuilder query,
                                                        final String indexName,
                                                        final String firstField,
                                                        final String secondField,
                                                        final String pathForNestedStatsAgg,
                                                        final BoolQueryBuilder filterQueryToWrapStatsWith) {
    final boolean combinedReportRangeProvided = context.getDateIntervalRange().isPresent();
    if (combinedReportRangeProvided) {
      final Range<OffsetDateTime> combinedRange = context.getDateIntervalRange().get();
      return new MinMaxStatDto(
        combinedRange.getMinimum().toInstant().toEpochMilli(),
        combinedRange.getMaximum().toInstant().toEpochMilli(),
        combinedRange.getMinimum().format(dateTimeFormatter),
        combinedRange.getMaximum().format(dateTimeFormatter)
      );
    } else {
      return getCrossFieldMinMaxStats(
        query,
        indexName,
        firstField,
        secondField,
        OPTIMIZE_DATE_FORMAT,
        pathForNestedStatsAgg,
        filterQueryToWrapStatsWith
      );
    }
  }

  public MinMaxStatDto getMinMaxNumberRangeForNestedField(final ExecutionContext<? extends SingleReportDataDto> context,
                                                          final QueryBuilder query,
                                                          final String indexName,
                                                          final String field,
                                                          final String pathForNestedStatsAgg,
                                                          final BoolQueryBuilder filterQueryToWrapStatsWith) {
    final boolean combinedReportRangeProvided = context.getNumberVariableRange().isPresent();
    if (combinedReportRangeProvided) {
      final Range<Double> combinedRange = context.getNumberVariableRange().get();
      return new MinMaxStatDto(
        combinedRange.getMinimum(),
        combinedRange.getMaximum(),
        combinedRange.getMinimum().toString(),
        combinedRange.getMaximum().toString()
      );
    } else {
      return getSingleFieldMinMaxStats(query, indexName, field, pathForNestedStatsAgg, filterQueryToWrapStatsWith);
    }
  }

  public MinMaxStatDto getScriptedMinMaxStats(final QueryBuilder query,
                                              final String indexName,
                                              final Script script) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(AggregationBuilders.stats(STATS_AGGREGATION_FIRST_FIELD).script(script))
      .size(0);
    final SearchRequest searchRequest = new SearchRequest(indexName).source(searchSourceBuilder);
    final SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String reason = String.format(
        "Could not retrieve stats for script %s on index %s", script.toString(), indexName
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    final Stats minMaxStats = response.getAggregations().get(STATS_AGGREGATION_FIRST_FIELD);
    return new MinMaxStatDto(
      minMaxStats.getMin(), minMaxStats.getMax(), minMaxStats.getMinAsString(), minMaxStats.getMaxAsString()
    );
  }

  private MinMaxStatDto getSingleFieldMinMaxStats(final QueryBuilder query,
                                                  final String indexName,
                                                  final String field,
                                                  final String pathForNestedStatsAgg,
                                                  final BoolQueryBuilder filterQueryToWrapStatsWith) {
    return getCrossFieldMinMaxStats(
      query, indexName, field, field, null, pathForNestedStatsAgg, filterQueryToWrapStatsWith
    );
  }

  private MinMaxStatDto getCrossFieldMinMaxStats(final QueryBuilder query,
                                                 final String indexName,
                                                 final String firstField,
                                                 final String secondField,
                                                 final String format,
                                                 final String pathForNestedStatsAgg,
                                                 final BoolQueryBuilder filterQueryToWrapStatsWith) {
    AggregationBuilder statsAggField1 = createStatsAggregation(
      STATS_AGGREGATION_FIRST_FIELD,
      firstField,
      format
    );
    AggregationBuilder statsAggField2 = createStatsAggregation(
      STATS_AGGREGATION_SECOND_FIELD,
      secondField,
      format
    );

    if (filterQueryToWrapStatsWith != null) {
      statsAggField1 = wrapWithFilterLimitedParentAggregation(
        FILTER_AGGREGATION_FIRST_FIELD,
        filterQueryToWrapStatsWith,
        statsAggField1
      );
      statsAggField2 = wrapWithFilterLimitedParentAggregation(
        FILTER_AGGREGATION_SECOND_FIELD,
        filterQueryToWrapStatsWith,
        statsAggField2
      );
    }

    if (pathForNestedStatsAgg != null) {
      statsAggField1 = nested(NESTED_AGGREGATION_FIRST_FIELD, pathForNestedStatsAgg).subAggregation(statsAggField1);
      statsAggField2 = nested(NESTED_AGGREGATION_SECOND_FIELD, pathForNestedStatsAgg).subAggregation(statsAggField2);
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(statsAggField1)
      .aggregation(statsAggField2)
      .size(0);
    SearchRequest searchRequest = new SearchRequest(indexName).source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Could not retrieve stats for firstField %s and secondField %s on index %s",
        firstField,
        secondField,
        indexName
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return mapCrossFieldStatAggregationsToStatDto(response);
  }

  private AggregationBuilder createStatsAggregation(final String aggregationName,
                                                    final String aggregationField,
                                                    final String format) {
    if (format == null) {
      return AggregationBuilders
        .stats(aggregationName)
        .field(aggregationField);
    } else {
      return AggregationBuilders
        .stats(aggregationName)
        .field(aggregationField)
        .format(format);
    }
  }

  private MinMaxStatDto mapCrossFieldStatAggregationsToStatDto(final SearchResponse response) {
    final Stats minMaxStats1 = retrieveNestedWrappedStatsAggregation(
      NESTED_AGGREGATION_FIRST_FIELD,
      FILTER_AGGREGATION_FIRST_FIELD,
      STATS_AGGREGATION_FIRST_FIELD,
      response
    );
    final Stats minMaxStats2 = retrieveNestedWrappedStatsAggregation(
      NESTED_AGGREGATION_SECOND_FIELD,
      FILTER_AGGREGATION_SECOND_FIELD,
      STATS_AGGREGATION_SECOND_FIELD,
      response
    );

    final Stats minStats = minMaxStats1.getMin() < minMaxStats2.getMin()
      ? minMaxStats1
      : minMaxStats2;
    final Stats maxStats = minMaxStats1.getMax() > minMaxStats2.getMax()
      ? minMaxStats1
      : minMaxStats2;

    return new MinMaxStatDto(
      minStats.getMin(),
      maxStats.getMax(),
      minStats.getMinAsString(),
      maxStats.getMaxAsString()
    );
  }

  private Stats retrieveNestedWrappedStatsAggregation(final String nestedAggName,
                                                      final String filterAggName,
                                                      final String statsAggName,
                                                      final SearchResponse response) {
    final Aggregations unnestedAggs = response.getAggregations().asMap().containsKey(nestedAggName)
      ? ((Nested) response.getAggregations().get(nestedAggName)).getAggregations()
      : response.getAggregations();
    final Optional<Aggregations> unwrappedAggs =
      unwrapFilterLimitedAggregations(filterAggName, unnestedAggs);
    return unwrappedAggs.orElse(unnestedAggs).get(statsAggName);
  }
}
