/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.service;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES.wrapWithFilterLimitedParentAggregation;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StatsAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.service.AbstractMinMaxStatsService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticSearchCondition.class)
@Component
public class MinMaxStatsServiceES extends AbstractMinMaxStatsService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MinMaxStatsServiceES.class);
  private final OptimizeElasticsearchClient esClient;

  public MinMaxStatsServiceES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
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

  public MinMaxStatDto getScriptedMinMaxStats(
      final Query query,
      final String[] indexNames,
      final String pathForNestedStatsAgg,
      final Script script,
      final Query filterQueryToWrapStatsWith) {
    final Supplier<Map<String, Aggregation.Builder.ContainerBuilder>> supplier =
        () -> {
          final Aggregation.Builder.ContainerBuilder builder =
              new Aggregation.Builder().stats(s -> s.script(script));
          Map<String, Aggregation.Builder.ContainerBuilder> statsAggregation =
              Map.of(STATS_AGGREGATION_FIRST_FIELD, builder);

          if (filterQueryToWrapStatsWith != null) {
            statsAggregation =
                wrapWithFilterLimitedParentAggregation(
                    FILTER_AGGREGATION_FIRST_FIELD, filterQueryToWrapStatsWith, statsAggregation);
          }
          return statsAggregation;
        };

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, indexNames)
                    .query(query)
                    .source(o -> o.fetch(false))
                    .size(0)
                    .aggregations(
                        Optional.ofNullable(pathForNestedStatsAgg)
                            .map(
                                nestedPath ->
                                    new Aggregation.Builder().nested(n -> n.path(nestedPath)))
                            .map(
                                nestedAgg -> {
                                  nestedAgg.aggregations(
                                      supplier.get().entrySet().stream()
                                          .collect(
                                              Collectors.toMap(
                                                  Map.Entry::getKey, e -> e.getValue().build())));
                                  return Map.of(NESTED_AGGREGATION_FIRST_FIELD, nestedAgg.build());
                                })
                            .orElse(
                                supplier.get().entrySet().stream()
                                    .collect(
                                        Collectors.toMap(
                                            Map.Entry::getKey, e -> e.getValue().build())))));
    final SearchResponse<?> response;
    try {
      response = esClient.search(searchRequest, Object.class);
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
    Map<String, Aggregation.Builder.ContainerBuilder> statsAggField1 =
        createStatsAggregation(STATS_AGGREGATION_FIRST_FIELD, firstField, format);
    Map<String, Aggregation.Builder.ContainerBuilder> statsAggField2 =
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
      final Aggregation.Builder.ContainerBuilder builder =
          new Aggregation.Builder().nested(n -> n.path(pathForNestedStatsAgg));
      builder.aggregations(
          statsAggField1.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build())));
      statsAggField1 = Map.of(NESTED_AGGREGATION_FIRST_FIELD, builder);

      final Aggregation.Builder.ContainerBuilder builder1 =
          new Aggregation.Builder().nested(n -> n.path(pathForNestedStatsAgg));
      builder1.aggregations(
          statsAggField2.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build())));
      statsAggField2 = Map.of(NESTED_AGGREGATION_SECOND_FIELD, builder1);
    }

    final SearchResponse<?> response;
    try {
      final Map<String, Aggregation.Builder.ContainerBuilder> finalStatsAggField = statsAggField1;
      final Map<String, Aggregation.Builder.ContainerBuilder> finalStatsAggField1 = statsAggField2;
      response =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  s ->
                      s.optimizeIndex(esClient, indexNames)
                          .query(query)
                          .source(o -> o.fetch(false))
                          .aggregations(
                              finalStatsAggField.entrySet().stream()
                                  .collect(
                                      Collectors.toMap(
                                          Map.Entry::getKey, e -> e.getValue().build())))
                          .aggregations(
                              finalStatsAggField1.entrySet().stream()
                                  .collect(
                                      Collectors.toMap(
                                          Map.Entry::getKey, e -> e.getValue().build())))
                          .size(0)),
              Object.class);
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

  private Map<String, Aggregation.Builder.ContainerBuilder> createStatsAggregation(
      final String aggregationName, final String aggregationField, final String format) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .stats(
                s -> {
                  s.field(aggregationField);
                  if (format != null) {
                    s.format(format);
                  }
                  return s;
                });
    return Map.of(aggregationName, builder);
  }

  private MinMaxStatDto mapCrossFieldStatAggregationsToStatDto(final SearchResponse response) {
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
        FilterLimitedAggregationUtilES.unwrapFilterLimitedAggregations(filterAggName, unnestedAggs);
    return unwrappedAggs.orElse(unnestedAggs).get(statsAggName).stats();
  }
}
