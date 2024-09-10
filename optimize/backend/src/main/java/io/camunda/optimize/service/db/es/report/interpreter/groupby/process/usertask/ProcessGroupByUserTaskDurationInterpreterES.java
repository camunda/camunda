/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.usertask;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createUserTaskFlowNodeTypeFilter;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_USER_TASK_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.command.util.DurationScriptUtilES;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DurationAggregationService;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByUserTaskDurationInterpreterES
    extends AbstractGroupByUserTaskInterpreterES {

  private final MinMaxStatsServiceES minMaxStatsService;
  private final DurationAggregationService durationAggregationService;
  @Getter private final DefinitionService definitionService;
  @Getter private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  @Getter private final ProcessViewInterpreterFacadeES viewInterpreter;

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_USER_TASK_DURATION);
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final UserTaskDurationTime userTaskDurationTime = getUserTaskDurationTime(context);
    return durationAggregationService
        .createLimitedGroupByScriptedUserTaskDurationAggregation(
            searchSourceBuilder,
            context,
            getDurationScript(userTaskDurationTime),
            userTaskDurationTime)
        .map(
            durationAggregation ->
                (AggregationBuilder)
                    createFilteredUserTaskAggregation(
                        context, searchSourceBuilder.query(), durationAggregation))
        .map(Collections::singletonList)
        .orElse(Collections.emptyList());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    compositeCommandResult.setGroupByKeyOfNumericType(true);
    compositeCommandResult.setDistributedByKeyOfNumericType(
        distributedByInterpreter.isKeyOfNumericType(context));
    getFilteredUserTaskAggregation(response)
        .ifPresent(
            userFilteredFlowNodes -> {
              final List<CompositeCommandResult.GroupByResult> durationHistogramData =
                  durationAggregationService.mapGroupByDurationResults(
                      response, userFilteredFlowNodes.getAggregations(), context);
              compositeCommandResult.setGroups(durationHistogramData);
            });
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQueryBuilder baseQuery) {
    return Optional.of(
        retrieveMinMaxDurationStats(context, baseQuery, getUserTaskDurationTime(context)));
  }

  private UserTaskDurationTime getUserTaskDurationTime(
      final ExecutionContext<ProcessReportDataDto, ?> context) {
    // groupBy is only supported on the first userTaskDurationTime, defaults to total
    return context.getReportConfiguration().getUserTaskDurationTimes().stream()
        .findFirst()
        .orElse(UserTaskDurationTime.TOTAL);
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final QueryBuilder baseQuery,
      final UserTaskDurationTime userTaskDurationTime) {
    return minMaxStatsService.getScriptedMinMaxStats(
        baseQuery,
        getIndexNames(context),
        FLOW_NODE_INSTANCES,
        getDurationScript(userTaskDurationTime),
        createUserTaskFlowNodeTypeFilter());
  }

  private Script getDurationScript(final UserTaskDurationTime userTaskDurationTime) {
    return DurationScriptUtilES.getUserTaskDurationScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        FLOW_NODE_INSTANCES + "." + userTaskDurationTime.getDurationFieldName());
  }
}
