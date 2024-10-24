/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.plan.process;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.ExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.interpreter.plan.HasGroupByMinMaxStats;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface ProcessExecutionPlanInterpreter
    extends ExecutionPlanInterpreter<ProcessReportDataDto, ProcessExecutionPlan>,
        HasGroupByMinMaxStats<ProcessReportDataDto, ProcessExecutionPlan> {

  // Instance date filters should also reduce the total count (baseline) considered for report
  // evaluation
  static final List<Class<? extends ProcessFilterDto<?>>> FILTERS_AFFECTING_BASELINE =
      List.of(
          InstanceStartDateFilterDto.class,
          InstanceEndDateFilterDto.class,
          FlowNodeStartDateFilterDto.class,
          FlowNodeEndDateFilterDto.class);

  default Map<String, List<ProcessFilterDto<?>>> getInstanceLevelDateFiltersByDefinitionKey(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getReportData().groupFiltersByDefinitionIdentifier().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry ->
                    entry.getValue().stream()
                        .filter(
                            filter -> filter.getFilterLevel() == FilterApplicationLevel.INSTANCE)
                        .filter(filter -> FILTERS_AFFECTING_BASELINE.contains(filter.getClass()))
                        .toList()));
  }
}
