/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.usertask;

import static io.camunda.optimize.service.db.os.report.filter.util.ModelElementFilterQueryUtilOS.createUserTaskFlowNodeTypeFilter;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_USER_TASK_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.DistributedByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.util.DurationScriptUtilOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.ViewInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.service.DurationAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.MinMaxStatsServiceOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.interpreter.groupby.usertask.ProcessGroupByUserTaskInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByUserTaskDurationInterpreterOS
    extends AbstractGroupByUserTaskInterpreterOS {

  private final MinMaxStatsServiceOS minMaxStatsService;
  private final DurationAggregationServiceOS durationAggregationService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;
  private final ProcessGroupByUserTaskInterpreterHelper helper;

  public ProcessGroupByUserTaskDurationInterpreterOS(
      final MinMaxStatsServiceOS minMaxStatsService,
      final DurationAggregationServiceOS durationAggregationService,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter,
      final ProcessGroupByUserTaskInterpreterHelper helper) {
    super();
    this.minMaxStatsService = minMaxStatsService;
    this.durationAggregationService = durationAggregationService;
    this.definitionService = definitionService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.helper = helper;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_USER_TASK_DURATION);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final UserTaskDurationTime userTaskDurationTime = getHelper().getUserTaskDurationTime(context);
    return durationAggregationService
        .createLimitedGroupByScriptedUserTaskDurationAggregation(
            boolQuery, context, getDurationScript(userTaskDurationTime), userTaskDurationTime)
        .map(e -> createFilteredUserTaskAggregation(context, boolQuery, e.getKey(), e.getValue()))
        .orElse(Map.of());
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return Optional.of(
        retrieveMinMaxDurationStats(
            context, baseQuery, getHelper().getUserTaskDurationTime(context)));
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    compositeCommandResult.setGroupByKeyOfNumericType(true);
    compositeCommandResult.setDistributedByKeyOfNumericType(
        distributedByInterpreter.isKeyOfNumericType(context));
    getFilteredUserTaskAggregation(response)
        .ifPresent(
            userFilteredFlowNodes -> {
              final List<CompositeCommandResult.GroupByResult> durationHistogramData =
                  durationAggregationService.mapGroupByDurationResults(
                      response, userFilteredFlowNodes.aggregations(), context);
              compositeCommandResult.setGroups(durationHistogramData);
            });
  }

  @Override
  protected DistributedByInterpreterOS<ProcessReportDataDto, ProcessExecutionPlan>
      getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  protected ViewInterpreterOS<ProcessReportDataDto, ProcessExecutionPlan> getViewInterpreter() {
    return viewInterpreter;
  }

  @Override
  protected DefinitionService getDefinitionService() {
    return definitionService;
  }

  @Override
  public ProcessGroupByUserTaskInterpreterHelper getHelper() {
    return helper;
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery,
      final UserTaskDurationTime userTaskDurationTime) {
    return minMaxStatsService.getScriptedMinMaxStats(
        baseQuery,
        getIndexNames(context),
        FLOW_NODE_INSTANCES,
        getDurationScript(userTaskDurationTime),
        Query.of(q -> q.bool(createUserTaskFlowNodeTypeFilter().build())));
  }

  private Script getDurationScript(final UserTaskDurationTime userTaskDurationTime) {
    return DurationScriptUtilOS.getUserTaskDurationScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        FLOW_NODE_INSTANCES + "." + userTaskDurationTime.getDurationFieldName());
  }
}
