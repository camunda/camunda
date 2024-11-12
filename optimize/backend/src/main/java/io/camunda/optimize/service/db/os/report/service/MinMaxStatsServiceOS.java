/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.service;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.sourceExcluded;
import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.unwrapFilterLimitedAggregations;
import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.wrapWithFilterLimitedParentAggregation;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.service.AbstractMinMaxStatsService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StatsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StatsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class MinMaxStatsServiceOS extends AbstractMinMaxStatsService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MinMaxStatsServiceOS.class);
  private final OptimizeOpenSearchClient osClient;

  public MinMaxStatsServiceOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  public MinMaxStatDto getMinMaxDateRange(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final Query query,
      final String[] indexNames,
      final String field) {
    return getMinMaxDateRangeForNestedField(context, query, indexNames, field, field, null, null);
  }

  public MinMaxStatDto getMinMaxDateRangeForCrossField(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final Query query,
      final String[] indexNames,
      final String firstField,
      final String secondField) {
    return getMinMaxDateRangeForNestedField(
        context, query, indexNames, firstField, secondField, null, null);
  }

  public MinMaxStatDto getMinMaxDateRangeForNestedField(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final Query query,
      final String[] indexNames,
      final String field,
      final String pathForNestedStatsAgg,
      final Query filterQueryToWrapStatsWith) {
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
      final Query query,
      final String[] indexNames,
      final Script script) {
    return context
        .getCombinedRangeMinMaxStats()
        .orElseGet(() -> getScriptedMinMaxStats(query, indexNames, null, script));
  }

  public MinMaxStatDto getMinMaxNumberRangeForNestedScriptedField(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final Query query,
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
      final Query query,
      final String[] indexNames,
      final String pathForNestedStatsAgg,
      final Script script,
      final Query filterQueryToWrapStatsWith) {
    return context
        .getCombinedRangeMinMaxStats()
        .orElseGet(
            () ->
                getScriptedMinMaxStats(
                    query, indexNames, pathForNestedStatsAgg, script, filterQueryToWrapStatsWith));
  }

  public MinMaxStatDto getScriptedMinMaxStats(
      final Query query,
      final String[] indexNames,
      final String pathForNestedStatsAgg,
      final Script script) {
    return getScriptedMinMaxStats(query, indexNames, pathForNestedStatsAgg, script, null);
  }

  private Pair<String, Aggregation> buildFilteredAggregation(
      final Query filterQuery, final Pair<String, Aggregation> basicAggregation) {
    return wrapWithFilterLimitedParentAggregation(
        FILTER_AGGREGATION_FIRST_FIELD, filterQuery, basicAggregation);
  }

  private Pair<String, Aggregation> buildNestedAggregation(
      final String nestedPath, final Pair<String, Aggregation> aggregation) {
    return Pair.of(
        NESTED_AGGREGATION_FIRST_FIELD,
        new Aggregation.Builder()
            .nested(b -> b.path(nestedPath))
            .aggregations(aggregation.getKey(), aggregation.getValue())
            .build());
  }

  private Pair<String, Aggregation> buildStatsAggregation(
      final Script script, final Query filterQuery, final String nestedPath) {
    final Pair<String, Aggregation> basicAggregation =
        Pair.of(
            STATS_AGGREGATION_FIRST_FIELD,
            new StatsAggregation.Builder().script(script).build()._toAggregation());

    final Pair<String, Aggregation> aggregation =
        filterQuery == null
            ? basicAggregation
            : buildFilteredAggregation(filterQuery, basicAggregation);
    return nestedPath == null ? aggregation : buildNestedAggregation(nestedPath, aggregation);
  }

  public MinMaxStatDto getScriptedMinMaxStats(
      final Query query,
      final String[] indexNames,
      final String pathForNestedStatsAgg,
      final Script script,
      final Query filterQueryToWrapStatsWith) {
    final Pair<String, Aggregation> statsAggregation =
        buildStatsAggregation(script, filterQueryToWrapStatsWith, pathForNestedStatsAgg);

    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(osClient.applyIndexPrefixes(indexNames))
            .query(query)
            .source(sourceExcluded())
            .aggregations(statsAggregation.getKey(), statsAggregation.getValue())
            .size(0)
            .build();

    final SearchResponse<?> response;
    try {
      response = osClient.searchUnsafe(searchRequest, Object.class);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Could not retrieve stats for script %s on indices %s",
              script.toString(), Arrays.toString(indexNames));
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final RuntimeException e) {
      return returnEmptyResultIfInstanceIndexNotFound(e, indexNames);
    }

    final StatsAggregate minMaxStats =
        retrieveNestedWrappedStatsAggregation(
            NESTED_AGGREGATION_FIRST_FIELD,
            FILTER_AGGREGATION_FIRST_FIELD,
            STATS_AGGREGATION_FIRST_FIELD,
            response);
    return new MinMaxStatDto(minMaxStats.min(), minMaxStats.max());
  }

  public MinMaxStatDto getSingleFieldMinMaxStats(
      final Query query,
      final String[] indexNames,
      final String field,
      final String pathForNestedStatsAgg,
      final Query filterQueryToWrapStatsWith) {
    return getCrossFieldMinMaxStats(
        query, indexNames, field, field, null, pathForNestedStatsAgg, filterQueryToWrapStatsWith);
  }

  public MinMaxStatDto getSingleFieldMinMaxStats(
      final Query query,
      final String[] indexNames,
      final String field,
      final String format,
      final String pathForNestedStatsAgg,
      final Query filterQueryToWrapStatsWith) {
    return getCrossFieldMinMaxStats(
        query, indexNames, field, field, format, pathForNestedStatsAgg, filterQueryToWrapStatsWith);
  }

  private MinMaxStatDto getMinMaxDateRangeForNestedField(
      final ExecutionContext<? extends SingleReportDataDto, ?> context,
      final Query query,
      final String[] indexNames,
      final String firstField,
      final String secondField,
      final String pathForNestedStatsAgg,
      final Query filterQueryToWrapStatsWith) {
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
      final Query query,
      final String[] indexNames,
      final String firstField,
      final String secondField,
      final String format,
      final String pathForNestedStatsAgg,
      final Query filterQueryToWrapStatsWith) {
    Pair<String, Aggregation> statsAggField1 =
        Pair.of(STATS_AGGREGATION_FIRST_FIELD, createStatsAggregation(firstField, format));
    Pair<String, Aggregation> statsAggField2 =
        Pair.of(STATS_AGGREGATION_SECOND_FIELD, createStatsAggregation(secondField, format));

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
          Pair.of(
              NESTED_AGGREGATION_FIRST_FIELD,
              new Aggregation.Builder()
                  .nested(b -> b.path(pathForNestedStatsAgg))
                  .aggregations(statsAggField1.getKey(), statsAggField1.getValue())
                  .build());
      statsAggField2 =
          Pair.of(
              NESTED_AGGREGATION_SECOND_FIELD,
              new Aggregation.Builder()
                  .nested(b -> b.path(pathForNestedStatsAgg))
                  .aggregations(statsAggField2.getKey(), statsAggField2.getValue())
                  .build());
    }

    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(osClient.applyIndexPrefixes(indexNames))
            .query(query)
            .source(sourceExcluded())
            .aggregations(
                Map.of(
                    statsAggField1.getKey(), statsAggField1.getValue(),
                    statsAggField2.getKey(), statsAggField2.getValue()))
            .size(0)
            .build();
    final SearchResponse<?> response;
    try {
      response = osClient.searchUnsafe(searchRequest, Object.class);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Could not retrieve stats for firstField %s and secondField %s on index %s",
              firstField, secondField, Arrays.toString(indexNames));
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final RuntimeException e) {
      return returnEmptyResultIfInstanceIndexNotFound(e, indexNames);
    }
    return mapCrossFieldStatAggregationsToStatDto(response);
  }

  private Aggregation createStatsAggregation(final String aggregationField, final String format) {
    final StatsAggregation.Builder builder = new StatsAggregation.Builder().field(aggregationField);
    if (format != null) {
      builder.format(format);
    }
    return builder.build()._toAggregation();
  }

  private MinMaxStatDto mapCrossFieldStatAggregationsToStatDto(final SearchResponse<?> response) {
    final StatsAggregate minMaxStats1 =
        retrieveNestedWrappedStatsAggregation(
            NESTED_AGGREGATION_FIRST_FIELD,
            FILTER_AGGREGATION_FIRST_FIELD,
            STATS_AGGREGATION_FIRST_FIELD,
            response);
    final StatsAggregate minMaxStats2 =
        retrieveNestedWrappedStatsAggregation(
            NESTED_AGGREGATION_SECOND_FIELD,
            FILTER_AGGREGATION_SECOND_FIELD,
            STATS_AGGREGATION_SECOND_FIELD,
            response);

    final StatsAggregate minStats =
        minMaxStats1.min() < minMaxStats2.min() ? minMaxStats1 : minMaxStats2;
    final StatsAggregate maxStats =
        minMaxStats1.max() > minMaxStats2.max() ? minMaxStats1 : minMaxStats2;

    return new MinMaxStatDto(
        minStats.min(), maxStats.max(), minStats.minAsString(), maxStats.maxAsString());
  }

  private StatsAggregate retrieveNestedWrappedStatsAggregation(
      final String nestedAggName,
      final String filterAggName,
      final String statsAggName,
      final SearchResponse<?> response) {
    final Map<String, Aggregate> unnestedAggs =
        response.aggregations().containsKey(nestedAggName)
            ? response.aggregations().get(nestedAggName).nested().aggregations()
            : response.aggregations();
    final Optional<Map<String, Aggregate>> unwrappedAggs =
        unwrapFilterLimitedAggregations(filterAggName, unnestedAggs);
    return unwrappedAggs.orElse(unnestedAggs).get(statsAggName).stats();
  }
}
