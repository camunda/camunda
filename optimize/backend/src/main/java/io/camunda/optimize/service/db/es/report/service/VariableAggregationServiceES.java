/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.service;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.es.report.interpreter.groupby.AbstractGroupByVariableInterpreterES.FILTERED_FLOW_NODE_AGGREGATION;
import static java.util.stream.Collectors.toMap;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.HistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.MultiBucketAggregateBase;
import co.elastic.clients.elasticsearch._types.aggregations.MultiBucketBase;
import co.elastic.clients.elasticsearch._types.aggregations.ReverseNestedAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.es.report.context.DateAggregationContextES;
import io.camunda.optimize.service.db.es.report.context.VariableAggregationContextES;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Conditional(ElasticSearchCondition.class)
public class VariableAggregationServiceES {

  public static final String NESTED_VARIABLE_AGGREGATION = "nestedVariables";
  public static final String NESTED_FLOWNODE_AGGREGATION = "nestedFlowNodes";
  public static final String VARIABLES_AGGREGATION = "variables";
  public static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  public static final String FILTERED_INSTANCE_COUNT_AGGREGATION = "filteredInstCount";
  public static final String VARIABLES_INSTANCE_COUNT_AGGREGATION = "instCount";
  public static final String MISSING_VARIABLES_AGGREGATION = "missingVariables";
  public static final String VARIABLE_HISTOGRAM_AGGREGATION = "numberVariableHistogram";

  private final ConfigurationService configurationService;
  private final NumberVariableAggregationServiceES numberVariableAggregationService;
  private final DateAggregationServiceES dateAggregationService;
  private final MinMaxStatsServiceES minMaxStatsService;

  public Optional<Map<String, Aggregation.Builder.ContainerBuilder>> createVariableSubAggregation(
      final VariableAggregationContextES context) {
    context.setVariableRangeMinMaxStats(getVariableMinMaxStats(context));
    switch (context.getVariableType()) {
      case STRING:
      case BOOLEAN:
        final Aggregation.Builder.ContainerBuilder builder =
            new Aggregation.Builder()
                .terms(
                    t ->
                        t.field(context.getNestedVariableValueFieldLabel())
                            .size(
                                configurationService
                                    .getElasticSearchConfiguration()
                                    .getAggregationBucketLimit()));
        context.getSubAggregations().forEach((k, v) -> builder.aggregations(k, a -> v));
        return Optional.of(Map.of(VARIABLES_AGGREGATION, builder));
      case DATE:
        return createDateVariableAggregation(context);
      default:
        if (VariableType.getNumericTypes().contains(context.getVariableType())) {
          return numberVariableAggregationService.createNumberVariableAggregation(context);
        }

        return Optional.empty();
    }
  }

  public Optional<Query> createVariableFilterQuery(final VariableAggregationContextES context) {
    if (VariableType.getNumericTypes().contains(context.getVariableType())) {
      return numberVariableAggregationService
          .getBaselineForNumberVariableAggregation(context)
          .filter(baseLineValue -> !baseLineValue.isNaN())
          .map(
              baseLineValue ->
                  Query.of(
                      q ->
                          q.range(
                              r ->
                                  r.number(
                                      n ->
                                          n.field(context.getNestedVariableValueFieldLabel())
                                              .lte(context.getMaxVariableValue())
                                              .gte(baseLineValue)))));
    } else {
      return Optional.empty();
    }
  }

  private Optional<Map<String, Aggregation.Builder.ContainerBuilder>> createDateVariableAggregation(
      final VariableAggregationContextES context) {
    final DateAggregationContextES dateAggContext =
        DateAggregationContextES.builder()
            .aggregateByDateUnit(context.getDateUnit())
            .dateField(context.getNestedVariableValueFieldLabel())
            .minMaxStats(context.getVariableRangeMinMaxStats())
            .timezone(context.getTimezone())
            .subAggregations(context.getSubAggregations())
            .dateAggregationName(VARIABLES_AGGREGATION)
            .filterContext(context.getFilterContext())
            .build();

    return dateAggregationService.createDateVariableAggregation(dateAggContext);
  }

  public Map<String, Map<String, Aggregate>> retrieveResultBucketMap(
      final FilterAggregate filteredParentAgg,
      final Aggregate variableTermsAgg,
      final VariableType variableType,
      final ZoneId timezone) {
    final Map<String, Map<String, Aggregate>> bucketAggregations;
    if (VariableType.DATE.equals(variableType)) {
      bucketAggregations =
          dateAggregationService.mapDateAggregationsToKeyAggregationMap(
              (MultiBucketAggregateBase<? extends MultiBucketBase>)
                  filteredParentAgg.aggregations().get(VARIABLES_AGGREGATION)._get(),
              timezone);
    } else {
      bucketAggregations =
          (variableTermsAgg.isHistogram())
              ? variableTermsAgg.histogram().buckets().array().stream()
                  .collect(
                      toMap(
                          HistogramBucket::keyAsString,
                          MultiBucketBase::aggregations,
                          (bucketAggs1, bucketAggs2) -> bucketAggs1))
              : variableTermsAgg.sterms().buckets().array().stream()
                  .collect(
                      toMap(
                          a -> a.key().stringValue(),
                          MultiBucketBase::aggregations,
                          (bucketAggs1, bucketAggs2) -> bucketAggs1));
    }
    return bucketAggregations;
  }

  public Map<String, Aggregate> retrieveSubAggregationFromBucketMapEntry(
      final Map.Entry<String, Map<String, Aggregate>> bucketMapEntry) {
    final ReverseNestedAggregate reverseNested =
        bucketMapEntry.getValue().get(VARIABLES_INSTANCE_COUNT_AGGREGATION).reverseNested();
    if (reverseNested == null) {
      return bucketMapEntry.getValue();
    } else {
      final Aggregate nestedFlowNodeAgg =
          reverseNested.aggregations().get(NESTED_FLOWNODE_AGGREGATION);
      if (nestedFlowNodeAgg == null) {
        return reverseNested.aggregations(); // this is an instance report
      } else {
        final Map<String, Aggregate> flowNodeAggs =
            nestedFlowNodeAgg.nested().aggregations(); // this is a flownode report
        final FilterAggregate aggregation =
            flowNodeAggs.get(FILTERED_FLOW_NODE_AGGREGATION).filter();
        return aggregation.aggregations();
      }
    }
  }

  private MinMaxStatDto getVariableMinMaxStats(final VariableAggregationContextES context) {
    return getVariableMinMaxStats(
        context.getVariableType(),
        context.getVariableName(),
        context.getVariablePath(),
        context.getNestedVariableNameField(),
        context.getNestedVariableValueFieldLabel(),
        context.getIndexNames(),
        Query.of(q -> q.bool(context.getBaseQueryForMinMaxStats())));
  }

  public MinMaxStatDto getVariableMinMaxStats(
      final VariableType variableType,
      final String variableName,
      final String variablePath,
      final String nestedVariableNameField,
      final String nestedVariableValueFieldLabel,
      final String[] indexNames,
      final Query baseQuery) {
    final Query filterQuery =
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.must(
                            m ->
                                m.term(
                                    t -> t.field(nestedVariableNameField).value(variableName)))));
    if (VariableType.getNumericTypes().contains(variableType)) {
      return minMaxStatsService.getSingleFieldMinMaxStats(
          baseQuery, indexNames, nestedVariableValueFieldLabel, variablePath, filterQuery);
    } else if (VariableType.DATE.equals(variableType)) {
      return minMaxStatsService.getSingleFieldMinMaxStats(
          baseQuery,
          indexNames,
          nestedVariableValueFieldLabel,
          OPTIMIZE_DATE_FORMAT,
          variablePath,
          filterQuery);
    }
    return new MinMaxStatDto(Double.NaN, Double.NaN); // not used for other variable types
  }
}
