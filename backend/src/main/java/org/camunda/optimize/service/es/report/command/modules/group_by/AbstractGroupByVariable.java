/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.service.DateAggregationService;
import org.camunda.optimize.service.es.report.command.service.NumberVariableAggregationService;
import org.camunda.optimize.service.es.report.command.util.DateAggregationContext;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.FILTER_LIMITED_AGGREGATION;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;

@RequiredArgsConstructor
public abstract class AbstractGroupByVariable<Data extends SingleReportDataDto> extends GroupByPart<Data> {

  private static final String NESTED_AGGREGATION = "nested";
  private static final String VARIABLES_AGGREGATION = "variables";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String FILTERED_INSTANCE_COUNT_AGGREGATION = "filteredInstCount";
  private static final String VARIABLES_INSTANCE_COUNT_AGGREGATION = "inst_count";
  private static final String MISSING_VARIABLES_AGGREGATION = "missingVariables";
  public static final String RANGE_AGGREGATION = "rangeAggregation";

  private final ConfigurationService configurationService;
  private final DateAggregationService dateAggregationService;
  private final NumberVariableAggregationService numberVariableAggregationService;
  private final MinMaxStatsService minMaxStatsService;

  protected abstract String getVariableName(final ExecutionContext<Data> context);

  protected abstract VariableType getVariableType(final ExecutionContext<Data> context);

  protected abstract String getNestedVariableNameFieldLabel();

  protected abstract String getNestedVariableTypeField();

  protected abstract String getNestedVariableValueFieldLabel(final VariableType type);

  protected abstract String getVariablePath();

  protected abstract String getIndexName();

  protected abstract BoolQueryBuilder getVariableUndefinedOrNullQuery(final ExecutionContext<Data> context);

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(final ExecutionContext<Data> context,
                                                final BoolQueryBuilder baseQuery) {
    if (isGroupedByNumberVariable(getVariableType(context)) || VariableType.DATE.equals(getVariableType(context))) {
      return Optional.of(getMinMaxStats(baseQuery, context));
    }
    return Optional.empty();
  }

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<Data> context) {
    Optional<AggregationBuilder> variableSubAggregation =
      createVariableSubAggregation(context, searchSourceBuilder.query());

    if (!variableSubAggregation.isPresent()) {
      // if the report contains no instances and is grouped by date variable, this agg will not be present
      // as it is based on instance data
      return Collections.emptyList();
    }

    final NestedAggregationBuilder variableAggregation = nested(NESTED_AGGREGATION, getVariablePath())
      .subAggregation(
        filter(
          FILTERED_VARIABLES_AGGREGATION,
          boolQuery()
            .must(termQuery(getNestedVariableNameFieldLabel(), getVariableName(context)))
            .must(termQuery(getNestedVariableTypeField(), getVariableType(context).getId()))
            .must(existsQuery(getNestedVariableValueFieldLabel(VariableType.STRING)))
        )
          .subAggregation(variableSubAggregation.get())
          .subAggregation(reverseNested(FILTERED_INSTANCE_COUNT_AGGREGATION))
      );
    final AggregationBuilder undefinedOrNullVariableAggregation =
      createUndefinedOrNullVariableAggregation(context);
    return Arrays.asList(variableAggregation, undefinedOrNullVariableAggregation);
  }

  private AggregationBuilder createUndefinedOrNullVariableAggregation(final ExecutionContext<Data> context) {
    return filter(
      MISSING_VARIABLES_AGGREGATION,
      getVariableUndefinedOrNullQuery(context)
    )
      .subAggregation(distributedByPart.createAggregation(context));
  }

  private Optional<AggregationBuilder> createVariableSubAggregation(final ExecutionContext<Data> context,
                                                                    final QueryBuilder baseQuery) {
    Optional<AggregationBuilder> aggregationBuilder = Optional.empty();
    final VariableType type = getVariableType(context);

    switch (getVariableType(context)) {
      case STRING:
      case BOOLEAN:
        aggregationBuilder = Optional.of(AggregationBuilders
                                           .terms(VARIABLES_AGGREGATION)
                                           .size(configurationService.getEsAggregationBucketLimit())
                                           .field(getNestedVariableValueFieldLabel(VariableType.STRING)));
        break;
      case DATE:
        aggregationBuilder = createDateVariableAggregation(baseQuery, context);
        break;
      default:
        if (VariableType.getNumericTypes().contains(type)) {
          aggregationBuilder =
            numberVariableAggregationService.createNumberVariableAggregation(
              context,
              getVariableType(context),
              getNestedVariableValueFieldLabel(getVariableType(context)),
              getMinMaxStats(baseQuery, context),
              context.getReportData().getConfiguration().getCustomBucket()
            );
        }
    }

    final AggregationBuilder nestedDistributedByAgg = reverseNested(VARIABLES_INSTANCE_COUNT_AGGREGATION)
      .subAggregation(distributedByPart.createAggregation(context));

    return aggregationBuilder.map(builder -> builder.subAggregation(nestedDistributedByAgg));
  }

  private Optional<AggregationBuilder> createDateVariableAggregation(final QueryBuilder baseQuery,
                                                                     final ExecutionContext<Data> context) {
    final DateAggregationContext dateAggContext = DateAggregationContext.builder()
      .groupByDateUnit(getGroupByDateUnit(context))
      .dateField(getNestedVariableValueFieldLabel(VariableType.DATE))
      .minMaxStats(getMinMaxStats(baseQuery, context))
      .timezone(context.getTimezone())
      .distributedBySubAggregation(distributedByPart.createAggregation(context))
      .dateAggregationName(VARIABLES_AGGREGATION)
      .build();

    return dateAggregationService.createProcessDateVariableAggregation(dateAggContext);
  }

  private MinMaxStatDto getMinMaxStats(final QueryBuilder baseQuery,
                                       final ExecutionContext<Data> context) {
    final BoolQueryBuilder filterQuery = boolQuery().must(
      termQuery(getNestedVariableNameFieldLabel(), getVariableName(context))
    );

    if (VariableType.DATE.equals(getVariableType(context))) {
      return minMaxStatsService.getMinMaxDateRangeForNestedField(
        context,
        baseQuery,
        getIndexName(),
        getNestedVariableValueFieldLabel(VariableType.DATE),
        getVariablePath(),
        filterQuery
      );
    } else {
      return minMaxStatsService.getMinMaxNumberRangeForNestedField(
        context,
        baseQuery,
        getIndexName(),
        getNestedVariableValueFieldLabel(getVariableType(context)),
        getVariablePath(),
        filterQuery
      );
    }
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<Data> context) {
    if (response.getAggregations() == null) {
      // aggregations are null if there are no instances in the report and it is grouped by date variable
      return;
    }

    final Nested nested = response.getAggregations().get(NESTED_AGGREGATION);
    final Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Filter filteredParentAgg = filteredVariables.getAggregations().get(FILTER_LIMITED_AGGREGATION);
    if (filteredParentAgg == null) {
      filteredParentAgg = filteredVariables;
    }
    MultiBucketsAggregation variableTerms = filteredParentAgg.getAggregations().get(VARIABLES_AGGREGATION);
    if (variableTerms == null) {
      variableTerms = filteredParentAgg.getAggregations().get(RANGE_AGGREGATION);
    }

    Map<String, Aggregations> bucketAggregations;
    if (VariableType.DATE.equals(getVariableType(context))) {
      bucketAggregations =
        dateAggregationService.mapDateAggregationsToKeyAggregationMap(
          filteredParentAgg.getAggregations(),
          context.getTimezone(),
          VARIABLES_AGGREGATION
        );
    } else {
      bucketAggregations =
        variableTerms.getBuckets().stream()
          .collect(toMap(
            MultiBucketsAggregation.Bucket::getKeyAsString,
            MultiBucketsAggregation.Bucket::getAggregations,
            (bucketAggs1, bucketAggs2) -> bucketAggs1
          ));
    }

    final List<GroupByResult> groupedData = new ArrayList<>();
    for (Map.Entry<String, Aggregations> keyToAggregationEntry : bucketAggregations.entrySet()) {
      final ReverseNested reverseNested = keyToAggregationEntry.getValue().get(VARIABLES_INSTANCE_COUNT_AGGREGATION);
      final List<DistributedByResult> distribution =
        distributedByPart.retrieveResult(
          response,
          reverseNested == null ? keyToAggregationEntry.getValue() : reverseNested.getAggregations(),
          context
        );
      groupedData.add(GroupByResult.createGroupByResult(keyToAggregationEntry.getKey(), distribution));
    }

    addMissingVariableBuckets(groupedData, response, context);

    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setIsComplete(isResultComplete(filteredVariables, variableTerms));
    if (VariableType.DATE.equals(getVariableType(context))) {
      compositeCommandResult.setSorting(new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC));
    }
    compositeCommandResult.setKeyIsOfNumericType(
      distributedByPart.isKeyOfNumericType(context).orElse(getSortByKeyIsOfNumericType(context))
    );
  }

  private void addMissingVariableBuckets(final List<GroupByResult> groupedData,
                                         final SearchResponse response,
                                         final ExecutionContext<Data> context) {
    final Nested nested = response.getAggregations().get(NESTED_AGGREGATION);
    final Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);

    final ReverseNested filteredInstAggr = filteredVariables.getAggregations()
      .get(FILTERED_INSTANCE_COUNT_AGGREGATION);
    final long filteredProcInstCount = filteredInstAggr.getDocCount();

    if (response.getHits().getTotalHits().value > filteredProcInstCount) {
      final Filter aggregation = response.getAggregations().get(MISSING_VARIABLES_AGGREGATION);
      final List<DistributedByResult> missingVarsOperationResult =
        distributedByPart.retrieveResult(response, aggregation.getAggregations(), context);
      groupedData.add(GroupByResult.createGroupByResult(MISSING_VARIABLE_KEY, missingVarsOperationResult));
    }
  }

  private boolean getSortByKeyIsOfNumericType(final ExecutionContext<Data> context) {
    return VariableType.getNumericTypes().contains(getVariableType(context));
  }

  private boolean isResultComplete(final Filter filteredVariables,
                                   final MultiBucketsAggregation variableTerms) {
    final long resultDocCount = variableTerms.getBuckets()
      .stream()
      .mapToLong(MultiBucketsAggregation.Bucket::getDocCount)
      .sum();
    return filteredVariables.getDocCount() == resultDocCount;
  }

  private GroupByDateUnit getGroupByDateUnit(final ExecutionContext<Data> context) {
    return context.getReportData().getConfiguration().getGroupByDateVariableUnit();
  }

  private boolean isGroupedByNumberVariable(final VariableType varType) {
    return VariableType.getNumericTypes().contains(varType);
  }
}
