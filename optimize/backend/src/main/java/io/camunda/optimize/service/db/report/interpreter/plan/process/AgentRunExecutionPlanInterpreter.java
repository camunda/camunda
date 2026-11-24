/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.plan.process;

import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Evaluates the agentRun views against the agent run index, one document per agent run. The run
 * index uses the process instance field names for the definition, tenant and end date, so the
 * process definition scoping and the instance end date filters apply to it unchanged.
 */
public interface AgentRunExecutionPlanInterpreter extends ProcessExecutionPlanInterpreter {

  List<Class<? extends ProcessFilterDto<?>>> SUPPORTED_FILTERS =
      List.of(InstanceEndDateFilterDto.class, CompletedInstancesOnlyFilterDto.class);

  @Override
  default Set<ProcessExecutionPlan> getSupportedExecutionPlans() {
    return Arrays.stream(ProcessExecutionPlan.values())
        .filter(
            plan -> plan.getView().getProcessViewDto().getEntity() == ProcessViewEntity.AGENT_RUN)
        .collect(toSet());
  }

  /** Other process filters read fields that agent runs do not have, so they are rejected. */
  static void validateFilters(final ProcessReportDataDto reportData) {
    validateFilters(reportData.getFilter());
  }

  static void validateFilters(final List<ProcessFilterDto<?>> filters) {
    filters.stream()
        .filter(filter -> !SUPPORTED_FILTERS.contains(filter.getClass()))
        .findFirst()
        .ifPresent(
            filter -> {
              throw new OptimizeValidationException(
                  String.format(
                      "The filter [%s] is not supported for agentRun views. Supported filters: "
                          + "instanceEndDate, completedInstancesOnly.",
                      filter.getClass().getSimpleName()));
            });
  }
}
