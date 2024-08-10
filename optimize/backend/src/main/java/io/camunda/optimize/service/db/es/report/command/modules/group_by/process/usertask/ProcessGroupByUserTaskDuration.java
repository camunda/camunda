/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.process.usertask;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtil.createUserTaskFlowNodeTypeFilter;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.DurationGroupByDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.MinMaxStatDto;
import io.camunda.optimize.service.db.es.report.MinMaxStatsService;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import io.camunda.optimize.service.db.es.report.command.service.DurationAggregationService;
import io.camunda.optimize.service.db.es.report.command.util.DurationScriptUtil;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByUserTaskDuration extends AbstractGroupByUserTask {

  private final MinMaxStatsService minMaxStatsService;
  private final DurationAggregationService durationAggregationService;

  public ProcessGroupByUserTaskDuration(
      final MinMaxStatsService minMaxStatsService,
      final DurationAggregationService durationAggregationService,
      final DefinitionService definitionService) {
    super(definitionService);
    this.minMaxStatsService = minMaxStatsService;
    this.durationAggregationService = durationAggregationService;
  }

  @Override
  public List<AggregationBuilder> createAggregation(
      final SearchSourceBuilder searchSourceBuilder,
      final ExecutionContext<ProcessReportDataDto> context) {
    final UserTaskDurationTime userTaskDurationTime = getUserTaskDurationTime(context);
    return durationAggregationService
        .createLimitedGroupByScriptedUserTaskDurationAggregation(
            searchSourceBuilder,
            context,
            distributedByPart,
            getDurationScript(userTaskDurationTime),
            userTaskDurationTime)
        .map(
            durationAggregation ->
                (AggregationBuilder)
                    createFilteredUserTaskAggregation(context, durationAggregation))
        .map(Collections::singletonList)
        .orElse(Collections.emptyList());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse response,
      final ExecutionContext<ProcessReportDataDto> context) {
    compositeCommandResult.setGroupByKeyOfNumericType(true);
    compositeCommandResult.setDistributedByKeyOfNumericType(
        distributedByPart.isKeyOfNumericType(context));
    getFilteredUserTaskAggregation(response)
        .ifPresent(
            userFilteredFlowNodes -> {
              final List<CompositeCommandResult.GroupByResult> durationHistogramData =
                  durationAggregationService.mapGroupByDurationResults(
                      response,
                      userFilteredFlowNodes.getAggregations(),
                      context,
                      distributedByPart);
              compositeCommandResult.setGroups(durationHistogramData);
            });
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new DurationGroupByDto());
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto> context, final BoolQueryBuilder baseQuery) {
    return Optional.of(
        retrieveMinMaxDurationStats(context, baseQuery, getUserTaskDurationTime(context)));
  }

  private UserTaskDurationTime getUserTaskDurationTime(
      final ExecutionContext<ProcessReportDataDto> context) {
    // groupBy is only supported on the first userTaskDurationTime, defaults to total
    return context.getReportConfiguration().getUserTaskDurationTimes().stream()
        .findFirst()
        .orElse(UserTaskDurationTime.TOTAL);
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(
      final ExecutionContext<ProcessReportDataDto> context,
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
    return DurationScriptUtil.getUserTaskDurationScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        FLOW_NODE_INSTANCES + "." + userTaskDurationTime.getDurationFieldName());
  }
}
