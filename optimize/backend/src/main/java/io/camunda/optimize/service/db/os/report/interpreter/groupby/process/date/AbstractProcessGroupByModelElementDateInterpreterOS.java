/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.date;

import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.unwrapFilterLimitedAggregations;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.os.report.context.DateAggregationContextOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.service.DateAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.MinMaxStatsServiceOS;
import io.camunda.optimize.service.db.os.util.AggregateHelperOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.util.types.MapUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public abstract class AbstractProcessGroupByModelElementDateInterpreterOS
    extends AbstractProcessGroupByInterpreterOS {
  private static final String ELEMENT_AGGREGATION = "elementAggregation";
  private static final String FILTERED_ELEMENTS_AGGREGATION = "filteredElements";
  private static final String MODEL_ELEMENT_TYPE_FILTER_AGGREGATION = "filteredElementsByType";

  protected abstract DateAggregationServiceOS getDateAggregationService();

  protected abstract MinMaxStatsServiceOS getMinMaxStatsService();

  protected abstract String getPathToElementField();

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final AggregateByDateUnit unit = getGroupByDateUnit(context.getReportData());
    final MinMaxStatDto stats =
        getMinMaxStatsService()
            .getMinMaxDateRangeForNestedField(
                context,
                query,
                getIndexNames(context),
                getDateField(),
                getPathToElementField(),
                getFilterBoolQuery(context));

    final DateAggregationContextOS dateAggContext =
        DateAggregationContextOS.builder()
            .aggregateByDateUnit(unit)
            .dateField(getDateField())
            .minMaxStats(stats)
            .timezone(context.getTimezone())
            .subAggregations(getDistributedByInterpreter().createAggregations(context, query))
            .filterContext(context.getFilterContext())
            .build();

    final Optional<Pair<String, Aggregation>> bucketLimitedHistogramAggregation =
        getDateAggregationService().createModelElementDateAggregation(dateAggContext);

    return bucketLimitedHistogramAggregation
        .map(
            agg ->
                wrapInNestedElementAggregation(
                    context, agg, getDistributedByInterpreter().createAggregations(context, query)))
        .orElseGet(Map::of);
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    if (context.getReportData().getGroupBy().getValue() instanceof DateGroupByValueDto) {
      final AggregateByDateUnit groupByDateUnit = getGroupByDateUnit(context.getReportData());
      if (AggregateByDateUnit.AUTOMATIC.equals(groupByDateUnit)) {
        return Optional.of(
            getMinMaxStatsService()
                .getMinMaxDateRangeForNestedField(
                    context,
                    baseQuery,
                    getIndexNames(context),
                    getDateField(),
                    getPathToElementField(),
                    getFilterBoolQuery(context)));
      }
    }
    return Optional.empty();
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult result,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    result.setGroups(processAggregations(response, context));
    result.setGroupBySorting(
        context
            .getReportConfiguration()
            .getSorting()
            .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC)));
  }

  private Map<String, Aggregation> wrapInNestedElementAggregation(
      final ExecutionContext<ProcessReportDataDto, ?> context,
      final Pair<String, Aggregation> aggregationToWrap,
      final Map<String, Aggregation> distributedBySubAggregations) {
    final Aggregation filteredElementsAggregation =
        new Aggregation.Builder()
            .filter(getFilterBoolQuery(context))
            .aggregations(MapUtil.createFromPair(aggregationToWrap))
            .build();

    final Aggregation modelElementTypeFilterAggregation =
        new Aggregation.Builder()
            .filter(getModelElementTypeFilterQuery())
            .aggregations(
                MapUtil.combineUniqueMaps(
                    Map.of(FILTERED_ELEMENTS_AGGREGATION, filteredElementsAggregation),
                    // sibling aggregation next to filtered userTask agg for distributedByPart
                    // for retrieval of all keys that should be present in distributedBy result via
                    // enrichContextWithAllExpectedDistributedByKeys
                    distributedBySubAggregations))
            .build();

    final Aggregation aggregation =
        new Aggregation.Builder()
            .nested(n -> n.path(getPathToElementField()))
            .aggregations(MODEL_ELEMENT_TYPE_FILTER_AGGREGATION, modelElementTypeFilterAggregation)
            .build();

    return Map.of(ELEMENT_AGGREGATION, aggregation);
  }

  private List<GroupByResult> processAggregations(
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Aggregate> fixedAggregations =
        AggregateHelperOS.withNullValues(response.hits().total().value(), response.aggregations());

    if (fixedAggregations == null || fixedAggregations.isEmpty()) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final NestedAggregate flowNodes = fixedAggregations.get(ELEMENT_AGGREGATION).nested();
    final FilterAggregate filteredFlowNodesByType =
        flowNodes.aggregations().get(MODEL_ELEMENT_TYPE_FILTER_AGGREGATION).filter();
    final FilterAggregate filteredFlowNodes =
        filteredFlowNodesByType.aggregations().get(FILTERED_ELEMENTS_AGGREGATION).filter();
    final Optional<Map<String, Aggregate>> maybeUnwrappedLimitedAggregations =
        unwrapFilterLimitedAggregations(filteredFlowNodes.aggregations());

    getDistributedByInterpreter()
        .enrichContextWithAllExpectedDistributedByKeys(
            context, filteredFlowNodesByType.aggregations());

    return maybeUnwrappedLimitedAggregations
        .map(
            unwrappedLimitedAggregations -> {
              final Map<String, Map<String, Aggregate>> keyToAggregationMap =
                  getDateAggregationService()
                      .mapDateAggregationsToKeyAggregationMap(
                          unwrappedLimitedAggregations, context.getTimezone());
              return mapKeyToAggMapToGroupByResults(keyToAggregationMap, response, context);
            })
        .orElse(List.of());
  }

  private List<GroupByResult> mapKeyToAggMapToGroupByResults(
      final Map<String, Map<String, Aggregate>> keyToAggregationMap,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return keyToAggregationMap.entrySet().stream()
        .map(
            stringBucketEntry ->
                GroupByResult.createGroupByResult(
                    stringBucketEntry.getKey(),
                    getDistributedByInterpreter()
                        .retrieveResult(response, stringBucketEntry.getValue(), context)))
        .collect(Collectors.toList());
  }

  private AggregateByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }

  protected abstract String getDateField();

  protected abstract Query getFilterBoolQuery(
      final ExecutionContext<ProcessReportDataDto, ?> context);

  protected abstract Query getModelElementTypeFilterQuery();
}
