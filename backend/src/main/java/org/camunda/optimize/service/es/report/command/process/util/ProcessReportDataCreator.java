/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;

import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByEndDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByFlowNode;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByNone;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByStartDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByVariable;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createCountFlowNodeFrequencyView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createCountProcessInstanceFrequencyView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createRawDataView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createUserTaskDurationView;

public class ProcessReportDataCreator {

  public static ProcessReportDataDto createFlowNodeDurationGroupByFlowNodeReport(AggregationType aggroType) {
    ProcessViewDto view = ProcessViewDtoCreator.createFlowNodeDurationView();
    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getConfiguration().setAggregationType(aggroType);
    return reportData;
  }

  public static ProcessReportDataDto createUserTaskIdleDurationGroupByUserTaskReport(AggregationType aggroType) {
    return createUserTaskReportWithViewAndConfiguration(aggroType, UserTaskDurationTime.IDLE);
  }

  public static ProcessReportDataDto createUserTaskTotalDurationGroupByUserTaskReport(AggregationType aggroType) {
    return createUserTaskReportWithViewAndConfiguration(aggroType, UserTaskDurationTime.TOTAL);
  }

  public static ProcessReportDataDto createUserTaskWorkDurationGroupByUserTaskReport(AggregationType aggroType) {
    return createUserTaskReportWithViewAndConfiguration(aggroType, UserTaskDurationTime.WORK);
  }

  private static ProcessReportDataDto createUserTaskReportWithViewAndConfiguration(final AggregationType aggroType,
                                                                                   final UserTaskDurationTime userTaskDurationTime) {
    final ProcessViewDto view = createUserTaskDurationView();
    final ProcessGroupByDto groupByDto = createGroupByFlowNode();

    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getConfiguration().setAggregationType(aggroType);
    reportData.getConfiguration().setUserTaskDurationTime(userTaskDurationTime);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByNoneReport(AggregationType aggroType) {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getConfiguration().setAggregationType(aggroType);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByNoneWithProcessPartReport(AggregationType aggroType) {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    reportData.getConfiguration().setAggregationType(aggroType);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByStartDateReport(AggregationType aggroType) {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getConfiguration().setAggregationType(aggroType);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByStartDateWithProcessPartReport(AggregationType aggroType) {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    reportData.getConfiguration().setAggregationType(aggroType);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByEndDateReport(AggregationType aggroType) {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByEndDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getConfiguration().setAggregationType(aggroType);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByEndDateWithProcessPartReport(AggregationType aggroType) {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByEndDateDto();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getParameters().setProcessPart(new ProcessPartDto());
    reportData.getConfiguration().setAggregationType(aggroType);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByVariableReport(AggregationType aggroType) {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable();

    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getConfiguration().setAggregationType(aggroType);
    return reportData;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByVariableWithProcessPartReport(AggregationType aggroType) {
    ProcessReportDataDto reportData = createProcessInstanceDurationGroupByVariableReport(aggroType);
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

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByEndDateReport() {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByEndDateDto();

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
