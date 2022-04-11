/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.decision;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByEvaluationDateTimeDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByEvaluationDateTimeValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.es.report.command.service.DateAggregationService;
import org.camunda.optimize.service.es.report.command.util.DateAggregationContext;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.EVALUATION_DATE_TIME;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionGroupByEvaluationDateTime extends DecisionGroupByPart {

  private final DateAggregationService dateAggregationService;
  private final MinMaxStatsService minMaxStatsService;
  private final DecisionQueryFilterEnhancer queryFilterEnhancer;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<DecisionReportDataDto> context) {
    final AggregateByDateUnit unit = getGroupBy(context.getReportData()).getUnit();
    return createAggregation(searchSourceBuilder, context, unit);
  }

  private List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                     final ExecutionContext<DecisionReportDataDto> context,
                                                     final AggregateByDateUnit unit) {
    final MinMaxStatDto stats = minMaxStatsService.getMinMaxDateRange(
      context,
      searchSourceBuilder.query(),
      getIndexNames(context),
      EVALUATION_DATE_TIME
    );

    final DateAggregationContext dateAggContext = DateAggregationContext.builder()
      .aggregateByDateUnit(unit)
      .dateField(EVALUATION_DATE_TIME)
      .minMaxStats(stats)
      .timezone(context.getTimezone())
      .subAggregations(distributedByPart.createAggregations(context))
      .decisionFilters(context.getReportData().getFilter())
      .decisionQueryFilterEnhancer(queryFilterEnhancer)
      .filterContext(context.getFilterContext())
      .build();

    return dateAggregationService.createDecisionEvaluationDateAggregation(dateAggContext)
      .map(Collections::singletonList)
      .orElse(Collections.emptyList());
  }

  @Override
  public void addQueryResult(final CompositeCommandResult result,
                             final SearchResponse response,
                             final ExecutionContext<DecisionReportDataDto> context) {
    result.setGroups(processAggregations(response, context));
    result.setGroupBySorting(
      context.getReportConfiguration()
        .getSorting()
        .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.DESC))
    );
  }

  private DecisionGroupByEvaluationDateTimeValueDto getGroupBy(final DecisionReportDataDto reportData) {
    return ((DecisionGroupByEvaluationDateTimeDto) reportData.getGroupBy())
      .getValue();
  }

  private List<GroupByResult> processAggregations(final SearchResponse response,
                                                  final ExecutionContext<DecisionReportDataDto> context) {
    final Aggregations aggregations = response.getAggregations();

    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final Optional<Aggregations> unwrappedLimitedAggregations = unwrapFilterLimitedAggregations(aggregations);
    Map<String, Aggregations> keyToAggregationMap;
    if (unwrappedLimitedAggregations.isPresent()) {
      keyToAggregationMap = dateAggregationService.mapDateAggregationsToKeyAggregationMap(
        unwrappedLimitedAggregations.get(),
        context.getTimezone()
      );
    } else {
      return Collections.emptyList();
    }
    return mapKeyToAggMapToGroupByResults(keyToAggregationMap, response, context);
  }

  private List<GroupByResult> mapKeyToAggMapToGroupByResults(final Map<String, Aggregations> keyToAggregationMap,
                                                             final SearchResponse response,
                                                             final ExecutionContext<DecisionReportDataDto> context) {
    return keyToAggregationMap
      .entrySet()
      .stream()
      .map(stringBucketEntry -> GroupByResult.createGroupByResult(
        stringBucketEntry.getKey(),
        distributedByPart.retrieveResult(response, stringBucketEntry.getValue(), context)
      ))
      .collect(Collectors.toList());
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto reportData) {
    reportData.setGroupBy(new DecisionGroupByEvaluationDateTimeDto());
  }
}
