/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view.process;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.VariableViewPropertyDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.Optional;

public class ProcessViewVariableInterpreterHelper {
  public static VariableViewPropertyDto getVariableViewDto(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getReportData().getView().getProperties().stream()
        .map(property -> property.getViewPropertyDtoIfOfType(VariableViewPropertyDto.class))
        .filter(Optional::isPresent)
        .map(Optional::get)
        // we take the first as only one variable view property is supported
        .findFirst()
        .orElseThrow(
            () ->
                new OptimizeRuntimeException(
                    "No variable view property found in report configuration"));
  }
}
