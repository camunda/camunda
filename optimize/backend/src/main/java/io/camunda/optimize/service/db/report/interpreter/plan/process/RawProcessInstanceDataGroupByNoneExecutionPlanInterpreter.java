/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.plan.process;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static io.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.export.CSVUtils;
import java.util.List;

public interface RawProcessInstanceDataGroupByNoneExecutionPlanInterpreter {

  @SuppressWarnings("unchecked")
  default void addNewVariablesAndDtoFieldsToTableColumnConfig(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> executionContext,
      final CommandEvaluationResult<Object> result) {
    final List<RawDataProcessInstanceDto> rawDataProcessInstanceDtos;
    try {
      rawDataProcessInstanceDtos = (List<RawDataProcessInstanceDto>) result.getFirstMeasureData();
    } catch (final ClassCastException e) {
      throw new OptimizeRuntimeException("Unexpected report evaluation result type", e);
    }

    final List<String> variableNames =
        rawDataProcessInstanceDtos.stream()
            .flatMap(
                rawDataProcessInstanceDto ->
                    rawDataProcessInstanceDto.getVariables().keySet().stream())
            .map(varKey -> VARIABLE_PREFIX + varKey)
            .toList();

    final TableColumnDto tableColumns =
        executionContext.getReportData().getConfiguration().getTableColumns();
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addNewAndRemoveUnexpectedFlowNodeDurationColumns(
        CSVUtils.extractAllPrefixedFlowNodeKeys(rawDataProcessInstanceDtos));
    tableColumns.addCountColumns(CSVUtils.extractAllPrefixedCountKeys());
    tableColumns.addDtoColumns(extractAllProcessInstanceDtoFieldKeys());
  }
}
