/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.service;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.util.DateAggregationContext;
import org.camunda.optimize.service.es.report.command.util.VariableAggregationContext;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.es.report.command.modules.group_by.AbstractGroupByVariable.FILTERED_FLOW_NODE_AGGREGATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@RequiredArgsConstructor
@Component
public class VariableAggregationService {

  public static final String NESTED_VARIABLE_AGGREGATION = "nestedVariables";
  public static final String NESTED_FLOWNODE_AGGREGATION = "nestedFlownodes";
  public static final String VARIABLES_AGGREGATION = "variables";
  public static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  public static final String FILTERED_INSTANCE_COUNT_AGGREGATION = "filteredInstCount";
  public static final String VARIABLES_INSTANCE_COUNT_AGGREGATION = "instCount";
  public static final String MISSING_VARIABLES_AGGREGATION = "missingVariables";
  public static final String VARIABLE_HISTOGRAM_AGGREGATION = "numberVariableHistogram";


  private final ConfigurationService configurationService;
  private final NumberVariableAggregationService numberVariableAggregationService;
  private final DateAggregationService dateAggregationService;
  private final MinMaxStatsService minMaxStatsService;

  public Optional<AggregationBuilder> createVariableSubAggregation(
    final VariableAggregationContext context) {
    context.setVariableRangeMinMaxStats(getVariableMinMaxStats(context));
    switch (context.getVariableType()) {
      case STRING:
      case BOOLEAN:
        return Optional.of(AggregationBuilders
                             .terms(VARIABLES_AGGREGATION)
                             .size(configurationService.getEsAggregationBucketLimit())
                             .field(context.getNestedVariableValueFieldLabel())
                             .subAggregation(context.getSubAggregation()));
      case DATE:
        return createDateVariableAggregation(context);
      default:
        if (VariableType.getNumericTypes().contains(context.getVariableType())) {
          return numberVariableAggregationService.createNumberVariableAggregation(context);
        }

        return Optional.empty();
    }
  }

  private Optional<AggregationBuilder> createDateVariableAggregation(
    final VariableAggregationContext context) {
    final DateAggregationContext dateAggContext = DateAggregationContext.builder()
      .aggregateByDateUnit(context.getDateUnit())
      .dateField(context.getNestedVariableValueFieldLabel())
      .minMaxStats(context.getVariableRangeMinMaxStats())
      .timezone(context.getTimezone())
      .subAggregation(context.getSubAggregation())
      .dateAggregationName(VARIABLES_AGGREGATION)
      .build();

    return dateAggregationService.createDateVariableAggregation(dateAggContext);
  }

  public Map<String, Aggregations> retrieveResultBucketMap(final Filter filteredParentAgg,
                                                           final MultiBucketsAggregation variableTermsAgg,
                                                           final VariableType variableType,
                                                           final ZoneId timezone) {
    Map<String, Aggregations> bucketAggregations;
    if (VariableType.DATE.equals(variableType)) {
      bucketAggregations =
        dateAggregationService.mapDateAggregationsToKeyAggregationMap(
          filteredParentAgg.getAggregations(),
          timezone,
          VARIABLES_AGGREGATION
        );
    } else {
      bucketAggregations =
        variableTermsAgg.getBuckets().stream()
          .collect(toMap(
            MultiBucketsAggregation.Bucket::getKeyAsString,
            MultiBucketsAggregation.Bucket::getAggregations,
            (bucketAggs1, bucketAggs2) -> bucketAggs1
          ));
    }
    return bucketAggregations;
  }

  public Aggregations retrieveSubAggregationFromBucketMapEntry(Map.Entry<String, Aggregations> bucketMapEntry) {
    final ReverseNested reverseNested = bucketMapEntry.getValue().get(VARIABLES_INSTANCE_COUNT_AGGREGATION);
    if (reverseNested == null) {
      return bucketMapEntry.getValue();
    } else {
      final ParsedNested nestedFlowNodeAgg = reverseNested.getAggregations().get(NESTED_FLOWNODE_AGGREGATION);
      if (nestedFlowNodeAgg == null) {
        return reverseNested.getAggregations(); // this is an instance report
      } else {
        Aggregations flowNodeAggs = nestedFlowNodeAgg.getAggregations(); // this is a flownode report
        final ParsedFilter aggregation = flowNodeAggs.get(FILTERED_FLOW_NODE_AGGREGATION);
        return aggregation.getAggregations();
      }
    }
  }

  private MinMaxStatDto getVariableMinMaxStats(final VariableAggregationContext context) {
    return getVariableMinMaxStats(
      context.getVariableType(),
      context.getVariableName(),
      context.getVariablePath(),
      context.getNestedVariableNameField(),
      context.getNestedVariableValueFieldLabel(),
      context.getIndexName(),
      context.getBaseQueryForMinMaxStats()
    );
  }

  public MinMaxStatDto getVariableMinMaxStats(final VariableType variableType,
                                              final String variableName,
                                              final String variablePath,
                                              final String nestedVariableNameField,
                                              final String nestedVariableValueFieldLabel,
                                              final String indexName,
                                              final QueryBuilder baseQuery) {
    final BoolQueryBuilder filterQuery = boolQuery().must(
      termQuery(nestedVariableNameField, variableName)
    );
    if (VariableType.getNumericTypes().contains(variableType)) {
      return minMaxStatsService.getSingleFieldMinMaxStats(
        baseQuery,
        indexName,
        nestedVariableValueFieldLabel,
        variablePath,
        filterQuery
      );
    } else if (VariableType.DATE.equals(variableType)) {
      return minMaxStatsService.getSingleFieldMinMaxStats(
        baseQuery,
        indexName,
        nestedVariableValueFieldLabel,
        OPTIMIZE_DATE_FORMAT,
        variablePath,
        filterQuery
      );
    }
    return new MinMaxStatDto(Double.NaN, Double.NaN); // not used for other variable types
  }

}
