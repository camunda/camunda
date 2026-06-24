/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.groupby.usertask;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.distributedby.DistributedByInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProcessGroupByUserTaskInterpreterHelper {

  private final DefinitionService definitionService;

  public ProcessGroupByUserTaskInterpreterHelper(final DefinitionService definitionService) {
    this.definitionService = definitionService;
  }

  public void addMissingGroupByResults(
      final Map<String, String> userTaskNames,
      final List<GroupByResult> groupedData,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final DistributedByInterpreter distributedByInterpreter) {
    final boolean viewLevelFilterExists =
        context.getReportData().getFilter().stream()
            .anyMatch(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()));
    // If a view level filter exists, the data should not be enriched as the missing data has been
    // omitted by the filters
    if (!viewLevelFilterExists) {
      // If no view level filter exists, we enrich the user task data with user tasks that may not
      // have been executed, but should still show up in the result
      userTaskNames.forEach(
          (key, value) ->
              groupedData.add(
                  GroupByResult.createGroupByResult(
                      key, value, distributedByInterpreter.createEmptyResult(context))));
    }
  }

  public void removeHiddenModelElements(
      final List<GroupByResult> groupedData,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (context.getHiddenFlowNodeIds() != null) {
      groupedData.removeIf(
          dataPoint -> context.getHiddenFlowNodeIds().contains(dataPoint.getKey()));
    }
  }

  public Map<String, String> getUserTaskNames(final ProcessReportDataDto reportData) {
    return definitionService.extractUserTaskIdAndNames(
        reportData.getDefinitions().stream()
            .map(
                definitionDto ->
                    definitionService.getDefinition(
                        DefinitionType.PROCESS,
                        definitionDto.getKey(),
                        definitionDto.getVersions(),
                        definitionDto.getTenantIds()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(ProcessDefinitionOptimizeDto.class::cast)
            .collect(Collectors.toList()));
  }

  public UserTaskDurationTime getUserTaskDurationTime(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    // groupBy is only supported on the first userTaskDurationTime, defaults to total
    return context.getReportConfiguration().getUserTaskDurationTimes().stream()
        .findFirst()
        .orElse(UserTaskDurationTime.TOTAL);
  }
}
