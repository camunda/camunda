/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.ElasticsearchStatusException;
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
import org.elasticsearch.search.aggregations.metrics.StatsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
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
                                                        final QueryBuilder filterQueryToWrapStatsWith) {
    return getMinMaxDateRangeForNestedField(
      context, query, indexName, field, field, pathForNestedStatsAgg, filterQueryToWrapStatsWith
    );
  }

  private MinMaxStatDto getMinMaxDateRangeForNestedField(final ExecutionContext<? extends SingleReportDataDto> context,
                                                         final QueryBuilder query,
                                                         final String indexName,
                                                         final String firstField,
                                                         final String secondField,
                                                         final String pathForNestedStatsAgg,
                                                         final QueryBuilder filterQueryToWrapStatsWith) {
    return context.getCombinedRangeMinMaxStats()
      .orElseGet(
        () -> getCrossFieldMinMaxStats(
          query, indexName, firstField, secondField, OPTIMIZE_DATE_FORMAT,
          pathForNestedStatsAgg, filterQueryToWrapStatsWith
        )
      );
  }

  public MinMaxStatDto getMinMaxNumberRangeForNestedField(final ExecutionContext<? extends SingleReportDataDto> context,
                                                          final QueryBuilder query,
                                                          final String indexName,
                                                          final String field,
                                                          final String pathForNestedStatsAgg,
                                                          final BoolQueryBuilder filterQueryToWrapStatsWith) {
    return context.getCombinedRangeMinMaxStats()
      .orElseGet(
        () -> getSingleFieldMinMaxStats(query, indexName, field, pathForNestedStatsAgg, filterQueryToWrapStatsWith)
      );
  }

  public MinMaxStatDto getMinMaxNumberRangeForScriptedField(final ExecutionContext<? extends SingleReportDataDto> context,
                                                            final QueryBuilder query,
                                                            final String indexName,
                                                            final Script script) {
    return context.getCombinedRangeMinMaxStats()
      .orElseGet(() -> getScriptedMinMaxStats(query, indexName, null, script));
  }

  public MinMaxStatDto getMinMaxNumberRangeForNestedScriptedField(
    final ExecutionContext<? extends SingleReportDataDto> context,
    final QueryBuilder query,
    final String indexName,
    final String pathForNestedStatsAgg,
    final Script script) {
    return context.getCombinedRangeMinMaxStats()
      .orElseGet(() -> getScriptedMinMaxStats(query, indexName, pathForNestedStatsAgg, script));
  }

  public MinMaxStatDto getScriptedMinMaxStats(final QueryBuilder query,
                                              final String indexName,
                                              final String pathForNestedStatsAgg,
                                              final Script script) {
    final StatsAggregationBuilder statsAggregation =
      AggregationBuilders.stats(STATS_AGGREGATION_FIRST_FIELD).script(script);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(
        Optional.ofNullable(pathForNestedStatsAgg)
          .map(nestedPath -> nested(NESTED_AGGREGATION_FIRST_FIELD, nestedPath))
          .map(nestedAgg -> (AggregationBuilder) nestedAgg.subAggregation(statsAggregation))
          .orElse(statsAggregation)
      )
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
    } catch (ElasticsearchStatusException e) {
      return returnEmptyResultIfInstanceIndexNotFound(e, indexName);
    }

    final Stats minMaxStats = Optional.ofNullable(pathForNestedStatsAgg)
      .map(paths -> ((Nested) response.getAggregations().get(NESTED_AGGREGATION_FIRST_FIELD)).getAggregations())
      .orElse(response.getAggregations())
      .get(STATS_AGGREGATION_FIRST_FIELD);
    return new MinMaxStatDto(minMaxStats.getMin(), minMaxStats.getMax());
  }

  public MinMaxStatDto getSingleFieldMinMaxStats(final QueryBuilder query,
                                                 final String indexName,
                                                 final String field,
                                                 final String pathForNestedStatsAgg,
                                                 final BoolQueryBuilder filterQueryToWrapStatsWith) {
    return getCrossFieldMinMaxStats(
      query, indexName, field, field, null, pathForNestedStatsAgg, filterQueryToWrapStatsWith
    );
  }

  public MinMaxStatDto getSingleFieldMinMaxStats(final QueryBuilder query,
                                                 final String indexName,
                                                 final String field,
                                                 final String format,
                                                 final String pathForNestedStatsAgg,
                                                 final BoolQueryBuilder filterQueryToWrapStatsWith) {
    return getCrossFieldMinMaxStats(
      query, indexName, field, field, format, pathForNestedStatsAgg, filterQueryToWrapStatsWith
    );
  }

  private MinMaxStatDto getCrossFieldMinMaxStats(final QueryBuilder query,
                                                 final String indexName,
                                                 final String firstField,
                                                 final String secondField,
                                                 final String format,
                                                 final String pathForNestedStatsAgg,
                                                 final QueryBuilder filterQueryToWrapStatsWith) {
    AggregationBuilder statsAggField1 = createStatsAggregation(STATS_AGGREGATION_FIRST_FIELD, firstField, format);
    AggregationBuilder statsAggField2 = createStatsAggregation(STATS_AGGREGATION_SECOND_FIELD, secondField, format);

    if (filterQueryToWrapStatsWith != null) {
      statsAggField1 = wrapWithFilterLimitedParentAggregation(
        FILTER_AGGREGATION_FIRST_FIELD, filterQueryToWrapStatsWith, statsAggField1);
      statsAggField2 = wrapWithFilterLimitedParentAggregation(
        FILTER_AGGREGATION_SECOND_FIELD, filterQueryToWrapStatsWith, statsAggField2);
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
    } catch (ElasticsearchStatusException e) {
      return returnEmptyResultIfInstanceIndexNotFound(e, indexName);
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
      minStats.getMin(), maxStats.getMax(), minStats.getMinAsString(), maxStats.getMaxAsString()
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

  private MinMaxStatDto returnEmptyResultIfInstanceIndexNotFound(final ElasticsearchStatusException e,
                                                                 final String indexName) {
    if (isInstanceIndexNotFoundException(e)) {
      log.info(
        "Could not calculate minMaxStats because required instance index with name {} does not exist. " +
          "Returning min and max 0 instead.",
        indexName,
        e
      );
      return new MinMaxStatDto(0, 0);
    }
    throw e;
  }
}
