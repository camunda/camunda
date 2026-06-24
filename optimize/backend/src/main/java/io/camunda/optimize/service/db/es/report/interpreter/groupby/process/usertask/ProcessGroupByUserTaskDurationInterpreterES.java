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

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.util.DurationScriptUtilES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DurationAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.MinMaxStatsServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.interpreter.groupby.usertask.ProcessGroupByUserTaskInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByUserTaskDurationInterpreterES
    extends AbstractGroupByUserTaskInterpreterES {

  private final MinMaxStatsServiceES minMaxStatsService;
  private final DurationAggregationServiceES durationAggregationService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ProcessGroupByUserTaskInterpreterHelper helper;

  public ProcessGroupByUserTaskDurationInterpreterES(
      final MinMaxStatsServiceES minMaxStatsService,
      final DurationAggregationServiceES durationAggregationService,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter,
      final ProcessGroupByUserTaskInterpreterHelper helper) {
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
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final UserTaskDurationTime userTaskDurationTime = getHelper().getUserTaskDurationTime(context);
    return durationAggregationService
        .createLimitedGroupByScriptedUserTaskDurationAggregation(
            boolQuery, context, getDurationScript(userTaskDurationTime), userTaskDurationTime)
        .map(
            durationAggregation ->
                durationAggregation.entrySet().stream()
                    .map(
                        e ->
                            createFilteredUserTaskAggregation(
                                context, boolQuery, e.getKey(), e.getValue().build()))
                    .findFirst()
                    .get())
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
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
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
  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return viewInterpreter;
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
    return DurationScriptUtilES.getUserTaskDurationScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        FLOW_NODE_INSTANCES + "." + userTaskDurationTime.getDurationFieldName());
  }

  @Override
  public DefinitionService getDefinitionService() {
    return definitionService;
  }

  @Override
  public ProcessGroupByUserTaskInterpreterHelper getHelper() {
    return helper;
  }
}
