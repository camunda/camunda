/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;

import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByFlowNode;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByNone;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByStartDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByVariable;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createCountFlowNodeFrequencyView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createCountProcessInstanceFrequencyView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createRawDataView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createUserTaskIdleDurationView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createUserTaskTotalDurationView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createUserTaskWorkDurationView;

public class ProcessReportDataCreator {

  public static ProcessReportDataDto createFlowNodeDurationGroupByFlowNodeReport() {
    ProcessViewDto view = ProcessViewDtoCreator.createFlowNodeDurationView();
    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createUserTaskIdleDurationGroupByUserTaskReport() {
    return createUserTaskReportWithView(createUserTaskIdleDurationView());
  }

  public static ProcessReportDataDto createUserTaskTotalDurationGroupByUserTaskReport() {
    return createUserTaskReportWithView(createUserTaskTotalDurationView());
  }

  public static ProcessReportDataDto createUserTaskWorkDurationGroupByUserTaskReport() {
    return createUserTaskReportWithView(createUserTaskWorkDurationView());
  }

  private static ProcessReportDataDto createUserTaskReportWithView(final ProcessViewDto view) {
    final ProcessGroupByDto groupByDto = createGroupByFlowNode();

    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByNoneReport() {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByNoneWithProcessPartReport() {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByStartDateReport() {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByStartDateWithProcessPartReport() {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByVariableReport() {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByVariableWithProcessPartReport() {
    ProcessReportDataDto reportData = createProcessInstanceDurationGroupByVariableReport();
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    return reportData;
  }

  public static ProcessReportDataDto createRawDataReport() {
    ProcessViewDto view = createRawDataView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByNoneReport() {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByStartDateReport() {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByVariableReport() {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByVariable();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createCountFlowNodeFrequencyGroupByFlowNodeReport() {
    ProcessViewDto view = createCountFlowNodeFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }


}
