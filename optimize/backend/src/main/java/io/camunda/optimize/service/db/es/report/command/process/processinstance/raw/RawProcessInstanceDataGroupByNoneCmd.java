/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.process.processinstance.raw;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static io.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.service.db.es.report.ReportEvaluationContext;
import io.camunda.optimize.service.db.es.report.command.ProcessCmd;
import io.camunda.optimize.service.db.es.report.command.exec.ProcessReportCmdExecutionPlan;
import io.camunda.optimize.service.db.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.process.none.ProcessGroupByNone;
import io.camunda.optimize.service.db.es.report.command.modules.view.process.ProcessViewRawData;
import io.camunda.optimize.service.export.CSVUtils;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RawProcessInstanceDataGroupByNoneCmd
    extends ProcessCmd<List<RawDataProcessInstanceDto>> {

  public RawProcessInstanceDataGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<List<RawDataProcessInstanceDto>> buildExecutionPlan(
      final ReportCmdExecutionPlanBuilder builder) {
    return builder
        .createExecutionPlan()
        .processCommand()
        .view(ProcessViewRawData.class)
        .groupBy(ProcessGroupByNone.class)
        .distributedBy(ProcessDistributedByNone.class)
        .<RawDataProcessInstanceDto>resultAsRawData()
        .build();
  }

  @Override
  public CommandEvaluationResult<List<RawDataProcessInstanceDto>> evaluate(
      final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto>
          reportEvaluationContext) {
    final CommandEvaluationResult<List<RawDataProcessInstanceDto>> commandResult =
        super.evaluate(reportEvaluationContext);
    addNewVariablesAndDtoFieldsToTableColumnConfig(reportEvaluationContext, commandResult);
    return commandResult;
  }

  private void addNewVariablesAndDtoFieldsToTableColumnConfig(
      final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto>
          reportEvaluationContext,
      final CommandEvaluationResult<List<RawDataProcessInstanceDto>> result) {
    final List<String> variableNames =
        result.getFirstMeasureData().stream()
            .flatMap(
                rawDataProcessInstanceDto ->
                    rawDataProcessInstanceDto.getVariables().keySet().stream())
            .map(varKey -> VARIABLE_PREFIX + varKey)
            .toList();

    TableColumnDto tableColumns =
        reportEvaluationContext
            .getReportDefinition()
            .getData()
            .getConfiguration()
            .getTableColumns();
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addNewAndRemoveUnexpectedFlowNodeDurationColumns(
        CSVUtils.extractAllPrefixedFlowNodeKeys(result.getFirstMeasureData()));
    tableColumns.addCountColumns(CSVUtils.extractAllPrefixedCountKeys());
    tableColumns.addDtoColumns(extractAllProcessInstanceDtoFieldKeys());
  }
}
