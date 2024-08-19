/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.decision;

import static io.camunda.optimize.service.db.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.EVALUATION_DATE_TIME;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByEvaluationDateTimeDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByEvaluationDateTimeValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.report.MinMaxStatDto;
import io.camunda.optimize.service.db.es.report.MinMaxStatsService;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.db.es.report.command.service.DateAggregationService;
import io.camunda.optimize.service.db.es.report.command.util.DateAggregationContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionGroupByEvaluationDateTime extends DecisionGroupByPart {

  private final DateAggregationService dateAggregationService;
  private final MinMaxStatsService minMaxStatsService;
  private final DecisionQueryFilterEnhancer queryFilterEnhancer;

  public DecisionGroupByEvaluationDateTime(
      final DateAggregationService dateAggregationService,
      final MinMaxStatsService minMaxStatsService,
      final DecisionQueryFilterEnhancer queryFilterEnhancer) {
    this.dateAggregationService = dateAggregationService;
    this.minMaxStatsService = minMaxStatsService;
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<DecisionReportDataDto> context) {
    final AggregateByDateUnit unit = getGroupBy(context.getReportData()).getUnit();
    return createAggregation(searchSourceBuilder, context, unit);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult result,
      final SearchResponse response,
      final ExecutionContext<DecisionReportDataDto> context) {
    result.setGroups(processAggregations(response, context));
    result.setGroupBySorting(
        context
            .getReportConfiguration()
            .getSorting()
            .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.DESC)));
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(
      final DecisionReportDataDto reportData) {
    reportData.setGroupBy(new DecisionGroupByEvaluationDateTimeDto());
  }

  private List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<DecisionReportDataDto> context,
      final AggregateByDateUnit unit) {
    final MinMaxStatDto stats =
        minMaxStatsService.getMinMaxDateRange(
            context, searchSourceBuilder.query(), getIndexNames(context), EVALUATION_DATE_TIME);

    final DateAggregationContext dateAggContext =
        DateAggregationContext.builder()
            .aggregateByDateUnit(unit)
            .dateField(EVALUATION_DATE_TIME)
            .minMaxStats(stats)
            .timezone(context.getTimezone())
            .subAggregations(distributedByPart.createAggregations(context))
            .decisionFilters(context.getReportData().getFilter())
            .decisionQueryFilterEnhancer(queryFilterEnhancer)
            .filterContext(context.getFilterContext())
            .build();

    return dateAggregationService
        .createDecisionEvaluationDateAggregation(dateAggContext)
        .map(Collections::singletonList)
        .orElse(Collections.emptyList());
  }

  private DecisionGroupByEvaluationDateTimeValueDto getGroupBy(
      final DecisionReportDataDto reportData) {
    return ((DecisionGroupByEvaluationDateTimeDto) reportData.getGroupBy()).getValue();
  }

  private List<GroupByResult> processAggregations(
      final SearchResponse response, final ExecutionContext<DecisionReportDataDto> context) {
    final Aggregations aggregations = response.getAggregations();

    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final Optional<Aggregations> unwrappedLimitedAggregations =
        unwrapFilterLimitedAggregations(aggregations);
    final Map<String, Aggregations> keyToAggregationMap;
    if (unwrappedLimitedAggregations.isPresent()) {
      keyToAggregationMap =
          dateAggregationService.mapDateAggregationsToKeyAggregationMap(
              unwrappedLimitedAggregations.get(), context.getTimezone());
    } else {
      return Collections.emptyList();
    }
    return mapKeyToAggMapToGroupByResults(keyToAggregationMap, response, context);
  }

  private List<GroupByResult> mapKeyToAggMapToGroupByResults(
      final Map<String, Aggregations> keyToAggregationMap,
      final SearchResponse response,
      final ExecutionContext<DecisionReportDataDto> context) {
    return keyToAggregationMap.entrySet().stream()
        .map(
            stringBucketEntry ->
                GroupByResult.createGroupByResult(
                    stringBucketEntry.getKey(),
                    distributedByPart.retrieveResult(
                        response, stringBucketEntry.getValue(), context)))
        .collect(Collectors.toList());
  }
}
