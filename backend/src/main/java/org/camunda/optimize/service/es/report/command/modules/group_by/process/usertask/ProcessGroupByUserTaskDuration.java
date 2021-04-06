/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.usertask;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.DurationGroupByDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.service.DurationAggregationService;
import org.camunda.optimize.service.es.report.command.util.DurationScriptUtil;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByUserTaskDuration extends AbstractGroupByUserTask {

  private final MinMaxStatsService minMaxStatsService;
  private final DurationAggregationService durationAggregationService;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final UserTaskDurationTime userTaskDurationTime = getUserTaskDurationTime(context);
    return durationAggregationService
      .createLimitedGroupByScriptedUserTaskDurationAggregation(
        searchSourceBuilder, context, distributedByPart, getDurationScript(userTaskDurationTime), userTaskDurationTime
      )
      .map(durationAggregation -> (AggregationBuilder) createFilteredUserTaskAggregation(context, durationAggregation))
      .map(Collections::singletonList)
      .orElse(Collections.emptyList());
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    compositeCommandResult.setGroupByKeyOfNumericType(true);
    compositeCommandResult.setDistributedByKeyOfNumericType(distributedByPart.isKeyOfNumericType(context));
    getFilteredUserTaskAggregation(response)
      .ifPresent(userFilteredFlowNodes -> {
        final List<CompositeCommandResult.GroupByResult> durationHistogramData =
          durationAggregationService.mapGroupByDurationResults(
            response, userFilteredFlowNodes.getAggregations(), context, distributedByPart
          );
        compositeCommandResult.setGroups(durationHistogramData);
      });
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new DurationGroupByDto());
  }

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(final ExecutionContext<ProcessReportDataDto> context,
                                                final BoolQueryBuilder baseQuery) {
    return Optional.of(retrieveMinMaxDurationStats(context, baseQuery, getUserTaskDurationTime(context)));
  }

  private UserTaskDurationTime getUserTaskDurationTime(final ExecutionContext<ProcessReportDataDto> context) {
    // groupBy is only supported on the first userTaskDurationTime, defaults to total
    return context.getReportConfiguration().getUserTaskDurationTimes().stream()
      .findFirst()
      .orElse(UserTaskDurationTime.TOTAL);
  }

  private MinMaxStatDto retrieveMinMaxDurationStats(final ExecutionContext<ProcessReportDataDto> context,
                                                    final QueryBuilder baseQuery,
                                                    final UserTaskDurationTime userTaskDurationTime) {
    return minMaxStatsService.getScriptedMinMaxStats(
      baseQuery, getIndexName(context), USER_TASKS, getDurationScript(userTaskDurationTime)
    );
  }

  private Script getDurationScript(final UserTaskDurationTime userTaskDurationTime) {
    return DurationScriptUtil.getUserTaskDurationScript(
      LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
      USER_TASKS + "." + userTaskDurationTime.getDurationFieldName()
    );
  }

}
