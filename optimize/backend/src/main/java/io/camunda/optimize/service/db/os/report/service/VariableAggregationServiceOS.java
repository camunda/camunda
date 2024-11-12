/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.service;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.gteLte;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.report.interpreter.groupby.AbstractGroupByVariableInterpreterOS.FILTERED_FLOW_NODE_AGGREGATION;
import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.os.report.context.DateAggregationContextOS;
import io.camunda.optimize.service.db.os.report.context.VariableAggregationContextOS;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramBucket;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.HistogramBucket;
import org.opensearch.client.opensearch._types.aggregations.MultiTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.RangeBucket;
import org.opensearch.client.opensearch._types.aggregations.ReverseNestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation.Builder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class VariableAggregationServiceOS {

  public static final String NESTED_VARIABLE_AGGREGATION = "nestedVariables";
  public static final String NESTED_FLOWNODE_AGGREGATION = "nestedFlowNodes";
  public static final String VARIABLES_AGGREGATION = "variables";
  public static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  public static final String FILTERED_INSTANCE_COUNT_AGGREGATION = "filteredInstCount";
  public static final String VARIABLES_INSTANCE_COUNT_AGGREGATION = "instCount";
  public static final String MISSING_VARIABLES_AGGREGATION = "missingVariables";
  public static final String VARIABLE_HISTOGRAM_AGGREGATION = "numberVariableHistogram";

  private final ConfigurationService configurationService;
  private final NumberVariableAggregationServiceOS numberVariableAggregationService;
  private final DateAggregationServiceOS dateAggregationService;
  private final MinMaxStatsServiceOS minMaxStatsService;

  public VariableAggregationServiceOS(
      final ConfigurationService configurationService,
      final NumberVariableAggregationServiceOS numberVariableAggregationService,
      final DateAggregationServiceOS dateAggregationService,
      final MinMaxStatsServiceOS minMaxStatsService) {
    this.configurationService = configurationService;
    this.numberVariableAggregationService = numberVariableAggregationService;
    this.dateAggregationService = dateAggregationService;
    this.minMaxStatsService = minMaxStatsService;
  }

  public Optional<Pair<String, Aggregation>> createVariableSubAggregation(
      final VariableAggregationContextOS context) {
    context.setVariableRangeMinMaxStats(getVariableMinMaxStats(context));
    switch (context.getVariableType()) {
      case STRING:
      case BOOLEAN:
        final TermsAggregation termsAggregation =
            new Builder()
                .field(context.getNestedVariableValueFieldLabel())
                .size(configurationService.getOpenSearchConfiguration().getAggregationBucketLimit())
                .build();
        return Optional.of(
            Pair.of(
                VARIABLES_AGGREGATION,
                new Aggregation.Builder()
                    .terms(termsAggregation)
                    .aggregations(context.getSubAggregations())
                    .build()));
      case DATE:
        return createDateVariableAggregation(context);
      default:
        if (VariableType.getNumericTypes().contains(context.getVariableType())) {
          return numberVariableAggregationService.createNumberVariableAggregation(context);
        }

        return Optional.empty();
    }
  }

  public Optional<Query> createVariableFilterQuery(final VariableAggregationContextOS context) {
    if (VariableType.getNumericTypes().contains(context.getVariableType())) {
      return numberVariableAggregationService
          .getBaselineForNumberVariableAggregation(context)
          .filter(baseLineValue -> !baseLineValue.isNaN())
          .map(
              baseLineValue ->
                  gteLte(
                      context.getNestedVariableValueFieldLabel(),
                      baseLineValue,
                      context.getMaxVariableValue()));
    } else {
      return Optional.empty();
    }
  }

  private Optional<Pair<String, Aggregation>> createDateVariableAggregation(
      final VariableAggregationContextOS context) {
    final DateAggregationContextOS dateAggContext =
        DateAggregationContextOS.builder()
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
      final Map<String, Map<String, Aggregate>> bucketMap,
      final VariableType variableType,
      final ZoneId timezone) {
    return VariableType.DATE.equals(variableType)
        ? dateAggregationService.mapDateAggregationsToKeyAggregationMap(
            filteredParentAgg.aggregations(), timezone, VARIABLES_AGGREGATION)
        : bucketMap;
  }

  public Map<String, Map<String, Aggregate>> resultBucketMap(final Aggregate aggregate) {
    if (aggregate.isMultiTerms()) {
      return aggregate.multiTerms().buckets().array().stream()
          .collect(
              toMap(
                  MultiTermsBucket::keyAsString,
                  MultiTermsBucket::aggregations,
                  (bucketAggs1, bucketAggs2) -> bucketAggs1));
    } else if (aggregate.isDateHistogram()) {
      return aggregate.dateHistogram().buckets().array().stream()
          .collect(
              toMap(
                  DateHistogramBucket::keyAsString,
                  DateHistogramBucket::aggregations,
                  (bucketAggs1, bucketAggs2) -> bucketAggs1));
    } else if (aggregate.isHistogram()) {
      return aggregate.histogram().buckets().array().stream()
          .collect(
              toMap(
                  HistogramBucket::keyAsString,
                  HistogramBucket::aggregations,
                  (bucketAggs1, bucketAggs2) -> bucketAggs1));
    } else if (aggregate.isSterms()) {
      return aggregate.sterms().buckets().array().stream()
          .collect(
              toMap(
                  StringTermsBucket::key,
                  StringTermsBucket::aggregations,
                  (bucketAggs1, bucketAggs2) -> bucketAggs1));
    } else if (aggregate.isDateRange()) {
      return aggregate.dateRange().buckets().array().stream()
          .collect(
              toMap(
                  RangeBucket::key,
                  RangeBucket::aggregations,
                  (bucketAggs1, bucketAggs2) -> bucketAggs1));
    } else {
      throw new IllegalArgumentException(
          "Unsupported aggregation type: " + aggregate._kind().name());
    }
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
      return nestedFlowNodeAgg == null
          // this is an instance report
          ? reverseNested.aggregations()
          // this is a flownode report
          : nestedFlowNodeAgg
              .nested()
              .aggregations()
              .get(FILTERED_FLOW_NODE_AGGREGATION)
              .filter()
              .aggregations();
    }
  }

  private MinMaxStatDto getVariableMinMaxStats(final VariableAggregationContextOS context) {
    return getVariableMinMaxStats(
        context.getVariableType(),
        context.getVariableName(),
        context.getVariablePath(),
        context.getNestedVariableNameField(),
        context.getNestedVariableValueFieldLabel(),
        context.getIndexNames(),
        context.getBaseQueryForMinMaxStats());
  }

  public MinMaxStatDto getVariableMinMaxStats(
      final VariableType variableType,
      final String variableName,
      final String variablePath,
      final String nestedVariableNameField,
      final String nestedVariableValueFieldLabel,
      final String[] indexNames,
      final Query baseQuery) {
    final Query filterQuery = term(nestedVariableNameField, variableName);
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
