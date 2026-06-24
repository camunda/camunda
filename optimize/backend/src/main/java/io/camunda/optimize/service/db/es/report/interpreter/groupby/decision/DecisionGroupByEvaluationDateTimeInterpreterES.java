/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.decision;

import static io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES.unwrapFilterLimitedAggregations;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_EVALUATION_DATE_TIME;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.EVALUATION_DATE_TIME;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByEvaluationDateTimeDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByEvaluationDateTimeValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.context.DateAggregationContextES;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.decision.DecisionDistributedByNoneInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.decision.DecisionViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionGroupByEvaluationDateTimeInterpreterES
    extends AbstractDecisionGroupByInterpreterES {

  private final DateAggregationServiceES dateAggregationService;
  private final MinMaxStatsServiceES minMaxStatsService;
  private final DecisionQueryFilterEnhancerES queryFilterEnhancer;
  private final DecisionDistributedByNoneInterpreterES distributedByInterpreter;
  private final DecisionViewInterpreterFacadeES viewInterpreter;

  public DecisionGroupByEvaluationDateTimeInterpreterES(
      final DateAggregationServiceES dateAggregationService,
      final MinMaxStatsServiceES minMaxStatsService,
      final DecisionQueryFilterEnhancerES queryFilterEnhancer,
      final DecisionDistributedByNoneInterpreterES distributedByInterpreter,
      final DecisionViewInterpreterFacadeES viewInterpreter) {
    this.dateAggregationService = dateAggregationService;
    this.minMaxStatsService = minMaxStatsService;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final AggregateByDateUnit unit = getGroupBy(context.getReportData()).getUnit();
    return createAggregation(boolQuery, context, unit);
  }

  private Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context,
      final AggregateByDateUnit unit) {
    final MinMaxStatDto stats =
        minMaxStatsService.getMinMaxDateRange(
            context,
            Query.of(q -> q.bool(boolQuery)),
            getIndexNames(context),
            EVALUATION_DATE_TIME);

    final DateAggregationContextES dateAggContext =
        DateAggregationContextES.builder()
            .aggregateByDateUnit(unit)
            .dateField(EVALUATION_DATE_TIME)
            .minMaxStats(stats)
            .timezone(context.getTimezone())
            .subAggregations(getDistributedByInterpreter().createAggregations(context, boolQuery))
            .decisionFilters(context.getReportData().getFilter())
            .decisionQueryFilterEnhancer(queryFilterEnhancer)
            .filterContext(context.getFilterContext())
            .build();

    return dateAggregationService
        .createDecisionEvaluationDateAggregation(dateAggContext)
        .orElse(Map.of());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult result,
      final ResponseBody<?> response,
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

  private List<CompositeCommandResult.GroupByResult> processAggregations(
      final ResponseBody<?> response,
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

  private List<CompositeCommandResult.GroupByResult> mapKeyToAggMapToGroupByResults(
      final Map<String, Map<String, Aggregate>> keyToAggregationMap,
      final ResponseBody<?> response,
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return keyToAggregationMap.entrySet().stream()
        .map(
            stringBucketEntry ->
                CompositeCommandResult.GroupByResult.createGroupByResult(
                    stringBucketEntry.getKey(),
                    getDistributedByInterpreter()
                        .retrieveResult(response, stringBucketEntry.getValue(), context)))
        .collect(Collectors.toList());
  }

  @Override
  public Set<DecisionGroupBy> getSupportedGroupBys() {
    return Set.of(DECISION_GROUP_BY_EVALUATION_DATE_TIME);
  }

  public DecisionDistributedByNoneInterpreterES getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public DecisionViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }
}
