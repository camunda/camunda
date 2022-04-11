/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.raw;

import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.service.es.report.ReportEvaluationContext;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.none.ProcessGroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewRawData;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static org.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;

@Component
public class RawProcessInstanceDataGroupByNoneCmd extends ProcessCmd<List<RawDataProcessInstanceDto>> {

  public RawProcessInstanceDataGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<List<RawDataProcessInstanceDto>> buildExecutionPlan(
    final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewRawData.class)
      .groupBy(ProcessGroupByNone.class)
      .distributedBy(ProcessDistributedByNone.class)
      .<RawDataProcessInstanceDto>resultAsRawData()
      .build();
  }

  @Override
  public CommandEvaluationResult<List<RawDataProcessInstanceDto>> evaluate(
    final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto> reportEvaluationContext) {
    final CommandEvaluationResult<List<RawDataProcessInstanceDto>> commandResult = super.evaluate(
      reportEvaluationContext);
    addNewVariablesAndDtoFieldsToTableColumnConfig(reportEvaluationContext, commandResult);
    return commandResult;
  }

  private void addNewVariablesAndDtoFieldsToTableColumnConfig(
    final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto> reportEvaluationContext,
    final CommandEvaluationResult<List<RawDataProcessInstanceDto>> result) {
    final List<String> variableNames = result.getFirstMeasureData()
      .stream()
      .flatMap(rawDataProcessInstanceDto -> rawDataProcessInstanceDto.getVariables().keySet().stream())
      .map(varKey -> VARIABLE_PREFIX + varKey)
      .collect(toList());

    TableColumnDto tableColumns = reportEvaluationContext.getReportDefinition()
      .getData()
      .getConfiguration()
      .getTableColumns();
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addDtoColumns(extractAllProcessInstanceDtoFieldKeys());
  }

}
