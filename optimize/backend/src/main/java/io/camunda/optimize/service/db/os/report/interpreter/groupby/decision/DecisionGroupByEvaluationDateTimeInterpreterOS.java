/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.decision;

import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.unwrapFilterLimitedAggregations;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_EVALUATION_DATE_TIME;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.EVALUATION_DATE_TIME;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByEvaluationDateTimeDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByEvaluationDateTimeValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.os.report.context.DateAggregationContextOS;
import io.camunda.optimize.service.db.os.report.filter.DecisionQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.decision.DecisionDistributedByNoneInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.decision.DecisionViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.service.DateAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.MinMaxStatsServiceOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.optimize.util.types.MapUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DecisionGroupByEvaluationDateTimeInterpreterOS
    extends AbstractDecisionGroupByInterpreterOS {

  private final DateAggregationServiceOS dateAggregationService;
  private final MinMaxStatsServiceOS minMaxStatsService;
  private final DecisionQueryFilterEnhancerOS queryFilterEnhancer;
  private final DecisionDistributedByNoneInterpreterOS distributedByInterpreter;
  private final DecisionViewInterpreterFacadeOS viewInterpreter;

  public DecisionGroupByEvaluationDateTimeInterpreterOS(
      final DateAggregationServiceOS dateAggregationService,
      final MinMaxStatsServiceOS minMaxStatsService,
      final DecisionQueryFilterEnhancerOS queryFilterEnhancer,
      final DecisionDistributedByNoneInterpreterOS distributedByInterpreter,
      final DecisionViewInterpreterFacadeOS viewInterpreter) {
    this.dateAggregationService = dateAggregationService;
    this.minMaxStatsService = minMaxStatsService;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<DecisionGroupBy> getSupportedGroupBys() {
    return Set.of(DECISION_GROUP_BY_EVALUATION_DATE_TIME);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final AggregateByDateUnit unit = getGroupBy(context.getReportData()).getUnit();
    return createAggregation(query, context, unit);
  }

  private Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context,
      final AggregateByDateUnit unit) {
    final MinMaxStatDto stats =
        minMaxStatsService.getMinMaxDateRange(
            context, query, getIndexNames(context), EVALUATION_DATE_TIME);

    final DateAggregationContextOS dateAggContext =
        DateAggregationContextOS.builder()
            .aggregateByDateUnit(unit)
            .dateField(EVALUATION_DATE_TIME)
            .minMaxStats(stats)
            .timezone(context.getTimezone())
            .subAggregations(distributedByInterpreter.createAggregations(context, query))
            .decisionFilters(context.getReportData().getFilter())
            .decisionQueryFilterEnhancer(queryFilterEnhancer)
            .filterContext(context.getFilterContext())
            .build();

    return dateAggregationService
        .createDecisionEvaluationDateAggregation(dateAggContext)
        .map(MapUtil::createFromPair)
        .orElse(Map.of());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult result,
      final SearchResponse<RawResult> response,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    result.setGroups(processAggregations(response, context));
    result.setGroupBySorting(
        context
            .getReportConfiguration()
            .getSorting()
            .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.DESC)));
  }

  private DecisionGroupByEvaluationDateTimeValueDto getGroupBy(
      final DecisionReportDataDto reportData) {
    return ((DecisionGroupByEvaluationDateTimeDto) reportData.getGroupBy()).getValue();
  }

  private List<GroupByResult> processAggregations(
      final SearchResponse<RawResult> response,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final Map<String, Aggregate> aggregations = response.aggregations();

    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final Optional<Map<String, Aggregate>> unwrappedLimitedAggregations =
        unwrapFilterLimitedAggregations(aggregations);
    final Map<String, Map<String, Aggregate>> keyToAggregationMap;
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
      final Map<String, Map<String, Aggregate>> keyToAggregationMap,
      final SearchResponse<RawResult> response,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return keyToAggregationMap.entrySet().stream()
        .map(
            stringBucketEntry ->
                GroupByResult.createGroupByResult(
                    stringBucketEntry.getKey(),
                    distributedByInterpreter.retrieveResult(
                        response, stringBucketEntry.getValue(), context)))
        .collect(Collectors.toList());
  }

  public DecisionDistributedByNoneInterpreterOS getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public DecisionViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }
}
