/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.service;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.es.report.command.util.FilterLimitedAggregationUtilES.unwrapFilterLimitedAggregations;
import static io.camunda.optimize.service.db.es.report.command.util.FilterLimitedAggregationUtilES.wrapWithFilterLimitedParentAggregation;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.service.AbstractMinMaxStatsService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.Stats;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticSearchCondition.class)
@RequiredArgsConstructor
@Component
@Slf4j
public class MinMaxStatsServiceES extends AbstractMinMaxStatsService {
  private final OptimizeElasticsearchClient esClient;

  public MinMaxStatDto getMinMaxDateRange(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final QueryBuilder query,
      final String[] indexNames,
      final String field) {
    return getMinMaxDateRangeForNestedField(context, query, indexNames, field, field, null, null);
  }

  public MinMaxStatDto getMinMaxDateRangeForCrossField(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final QueryBuilder query,
      final String[] indexNames,
      final String firstField,
      final String secondField) {
    return getMinMaxDateRangeForNestedField(
        context, query, indexNames, firstField, secondField, null, null);
  }

  public MinMaxStatDto getMinMaxDateRangeForNestedField(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final QueryBuilder query,
      final String[] indexNames,
      final String field,
      final String pathForNestedStatsAgg,
      final QueryBuilder filterQueryToWrapStatsWith) {
    return getMinMaxDateRangeForNestedField(
        context,
        query,
        indexNames,
        field,
        field,
        pathForNestedStatsAgg,
        filterQueryToWrapStatsWith);
  }

  public MinMaxStatDto getMinMaxNumberRangeForScriptedField(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final QueryBuilder query,
      final String[] indexNames,
      final Script script) {
    return context
        .getCombinedRangeMinMaxStats()
        .orElseGet(() -> getScriptedMinMaxStats(query, indexNames, null, script));
  }

  public MinMaxStatDto getMinMaxNumberRangeForNestedScriptedField(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final QueryBuilder query,
      final String[] indexNames,
      final String pathForNestedStatsAgg,
      final Script script) {
    return context
        .getCombinedRangeMinMaxStats()
        .orElseGet(
            () -> getScriptedMinMaxStats(query, indexNames, pathForNestedStatsAgg, script, null));
  }

  public MinMaxStatDto getMinMaxNumberRangeForNestedScriptedField(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final QueryBuilder query,
      final String[] indexNames,
      final String pathForNestedStatsAgg,
      final Script script,
      final BoolQueryBuilder filterQueryToWrapStatsWith) {
    return context
        .getCombinedRangeMinMaxStats()
        .orElseGet(
            () ->
                getScriptedMinMaxStats(
                    query, indexNames, pathForNestedStatsAgg, script, filterQueryToWrapStatsWith));
  }

  public MinMaxStatDto getScriptedMinMaxStats(
      final QueryBuilder query,
      final String[] indexNames,
      final String pathForNestedStatsAgg,
      final Script script) {
    return getScriptedMinMaxStats(query, indexNames, pathForNestedStatsAgg, script, null);
  }

  public MinMaxStatDto getScriptedMinMaxStats(
      final QueryBuilder query,
      final String[] indexNames,
      final String pathForNestedStatsAgg,
      final Script script,
      final BoolQueryBuilder filterQueryToWrapStatsWith) {
    AggregationBuilder statsAggregation =
        AggregationBuilders.stats(STATS_AGGREGATION_FIRST_FIELD).script(script);

    if (filterQueryToWrapStatsWith != null) {
      statsAggregation =
          wrapWithFilterLimitedParentAggregation(
              FILTER_AGGREGATION_FIRST_FIELD, filterQueryToWrapStatsWith, statsAggregation);
    }

    final AggregationBuilder finalStatsAggregation = statsAggregation;
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .fetchSource(false)
            .aggregation(
                Optional.ofNullable(pathForNestedStatsAgg)
                    .map(nestedPath -> nested(NESTED_AGGREGATION_FIRST_FIELD, nestedPath))
                    .map(
                        nestedAgg ->
                            (AggregationBuilder) nestedAgg.subAggregation(finalStatsAggregation))
                    .orElse(statsAggregation))
            .size(0);

    final SearchRequest searchRequest = new SearchRequest(indexNames).source(searchSourceBuilder);
    final SearchResponse response;
    try {
      response = esClient.search(searchRequest);
    } catch (IOException e) {
      final String reason =
          String.format(
              "Could not retrieve stats for script %s on indices %s",
              script.toString(), Arrays.toString(indexNames));
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (RuntimeException e) {
      return returnEmptyResultIfInstanceIndexNotFound(e, indexNames);
    }

    final Stats minMaxStats =
        retrieveNestedWrappedStatsAggregation(
            NESTED_AGGREGATION_FIRST_FIELD,
            FILTER_AGGREGATION_FIRST_FIELD,
            STATS_AGGREGATION_FIRST_FIELD,
            response);
    return new MinMaxStatDto(minMaxStats.getMin(), minMaxStats.getMax());
  }

  public MinMaxStatDto getSingleFieldMinMaxStats(
      final QueryBuilder query,
      final String[] indexNames,
      final String field,
      final String pathForNestedStatsAgg,
      final BoolQueryBuilder filterQueryToWrapStatsWith) {
    return getCrossFieldMinMaxStats(
        query, indexNames, field, field, null, pathForNestedStatsAgg, filterQueryToWrapStatsWith);
  }

  public MinMaxStatDto getSingleFieldMinMaxStats(
      final QueryBuilder query,
      final String[] indexNames,
      final String field,
      final String format,
      final String pathForNestedStatsAgg,
      final BoolQueryBuilder filterQueryToWrapStatsWith) {
    return getCrossFieldMinMaxStats(
        query, indexNames, field, field, format, pathForNestedStatsAgg, filterQueryToWrapStatsWith);
  }

  private MinMaxStatDto getMinMaxDateRangeForNestedField(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final QueryBuilder query,
      final String[] indexNames,
      final String firstField,
      final String secondField,
      final String pathForNestedStatsAgg,
      final QueryBuilder filterQueryToWrapStatsWith) {
    return context
        .getCombinedRangeMinMaxStats()
        .orElseGet(
            () ->
                getCrossFieldMinMaxStats(
                    query,
                    indexNames,
                    firstField,
                    secondField,
                    OPTIMIZE_DATE_FORMAT,
                    pathForNestedStatsAgg,
                    filterQueryToWrapStatsWith));
  }

  private MinMaxStatDto getCrossFieldMinMaxStats(
      final QueryBuilder query,
      final String[] indexNames,
      final String firstField,
      final String secondField,
      final String format,
      final String pathForNestedStatsAgg,
      final QueryBuilder filterQueryToWrapStatsWith) {
    AggregationBuilder statsAggField1 =
        createStatsAggregation(STATS_AGGREGATION_FIRST_FIELD, firstField, format);
    AggregationBuilder statsAggField2 =
        createStatsAggregation(STATS_AGGREGATION_SECOND_FIELD, secondField, format);

    if (filterQueryToWrapStatsWith != null) {
      statsAggField1 =
          wrapWithFilterLimitedParentAggregation(
              FILTER_AGGREGATION_FIRST_FIELD, filterQueryToWrapStatsWith, statsAggField1);
      statsAggField2 =
          wrapWithFilterLimitedParentAggregation(
              FILTER_AGGREGATION_SECOND_FIELD, filterQueryToWrapStatsWith, statsAggField2);
    }

    if (pathForNestedStatsAgg != null) {
      statsAggField1 =
          nested(NESTED_AGGREGATION_FIRST_FIELD, pathForNestedStatsAgg)
              .subAggregation(statsAggField1);
      statsAggField2 =
          nested(NESTED_AGGREGATION_SECOND_FIELD, pathForNestedStatsAgg)
              .subAggregation(statsAggField2);
    }

    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .fetchSource(false)
            .aggregation(statsAggField1)
            .aggregation(statsAggField2)
            .size(0);
    SearchRequest searchRequest = new SearchRequest(indexNames).source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason =
          String.format(
              "Could not retrieve stats for firstField %s and secondField %s on index %s",
              firstField, secondField, Arrays.toString(indexNames));
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (RuntimeException e) {
      return returnEmptyResultIfInstanceIndexNotFound(e, indexNames);
    }
    return mapCrossFieldStatAggregationsToStatDto(response);
  }

  private AggregationBuilder createStatsAggregation(
      final String aggregationName, final String aggregationField, final String format) {
    if (format == null) {
      return AggregationBuilders.stats(aggregationName).field(aggregationField);
    } else {
      return AggregationBuilders.stats(aggregationName).field(aggregationField).format(format);
    }
  }

  private MinMaxStatDto mapCrossFieldStatAggregationsToStatDto(final SearchResponse response) {
    final Stats minMaxStats1 =
        retrieveNestedWrappedStatsAggregation(
            NESTED_AGGREGATION_FIRST_FIELD,
            FILTER_AGGREGATION_FIRST_FIELD,
            STATS_AGGREGATION_FIRST_FIELD,
            response);
    final Stats minMaxStats2 =
        retrieveNestedWrappedStatsAggregation(
            NESTED_AGGREGATION_SECOND_FIELD,
            FILTER_AGGREGATION_SECOND_FIELD,
            STATS_AGGREGATION_SECOND_FIELD,
            response);

    final Stats minStats =
        minMaxStats1.getMin() < minMaxStats2.getMin() ? minMaxStats1 : minMaxStats2;
    final Stats maxStats =
        minMaxStats1.getMax() > minMaxStats2.getMax() ? minMaxStats1 : minMaxStats2;

    return new MinMaxStatDto(
        minStats.getMin(), maxStats.getMax(), minStats.getMinAsString(), maxStats.getMaxAsString());
  }

  private Stats retrieveNestedWrappedStatsAggregation(
      final String nestedAggName,
      final String filterAggName,
      final String statsAggName,
      final SearchResponse response) {
    final Aggregations unnestedAggs =
        response.getAggregations().asMap().containsKey(nestedAggName)
            ? ((Nested) response.getAggregations().get(nestedAggName)).getAggregations()
            : response.getAggregations();
    final Optional<Aggregations> unwrappedAggs =
        unwrapFilterLimitedAggregations(filterAggName, unnestedAggs);
    return unwrappedAggs.orElse(unnestedAggs).get(statsAggName);
  }
}
