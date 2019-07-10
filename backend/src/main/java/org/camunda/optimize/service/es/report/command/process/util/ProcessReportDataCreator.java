/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;

import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByAssignee;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByCandidateGroup;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByEndDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByFlowNode;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByNone;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByStartDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByVariable;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createCountFlowNodeFrequencyView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createCountProcessInstanceFrequencyView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createRawDataView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createUserTaskDurationView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createUserTaskFrequencyView;

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

  public static ProcessReportDataDto createUserTaskFrequencyGroupedByUserTaskReport() {
    final ProcessViewDto view = createUserTaskFrequencyView();
    final ProcessGroupByDto groupByDto = createGroupByFlowNode();

    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createUserTaskIdleDurationGroupByUserTaskReport(AggregationType aggroType) {
    return createUserTaskDurationGroupedByUserTaskReport(aggroType, UserTaskDurationTime.IDLE);
  }

  public static ProcessReportDataDto createUserTaskTotalDurationGroupByUserTaskReport(AggregationType aggroType) {
    return createUserTaskDurationGroupedByUserTaskReport(aggroType, UserTaskDurationTime.TOTAL);
  }

  public static ProcessReportDataDto createUserTaskWorkDurationGroupByUserTaskReport(AggregationType aggroType) {
    return createUserTaskDurationGroupedByUserTaskReport(aggroType, UserTaskDurationTime.WORK);
  }

  private static ProcessReportDataDto createUserTaskDurationGroupedByUserTaskReport(final AggregationType aggroType,
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

  public static ProcessReportDataDto createUserTaskFrequencyGroupByAssigneeReport() {
    final ProcessViewDto view = createUserTaskFrequencyView();
    final ProcessGroupByDto groupByDto = createGroupByAssignee();

    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createUserTaskFrequencyGroupByAssigneeByUserTaskReport() {
    final ProcessReportDataDto reportData = createUserTaskFrequencyGroupByAssigneeReport();
    reportData.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
    return reportData;
  }

  public static ProcessReportDataDto createUserTaskIdleDurationGroupByAssigneeReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByAssigneeReport(aggroType, UserTaskDurationTime.IDLE);
  }

  public static ProcessReportDataDto createUserTaskTotalDurationGroupByAssigneeReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByAssigneeReport(aggroType, UserTaskDurationTime.TOTAL);
  }

  public static ProcessReportDataDto createUserTaskWorkDurationGroupByAssigneeReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByAssigneeReport(aggroType, UserTaskDurationTime.WORK);
  }

  private static ProcessReportDataDto createUserTaskDurationGroupByAssigneeReport(final AggregationType aggroType,
                                                                                  final UserTaskDurationTime userTaskDurationTime) {
    final ProcessViewDto view = createUserTaskDurationView();
    final ProcessGroupByDto groupByDto = createGroupByAssignee();

    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getConfiguration().setAggregationType(aggroType);
    reportData.getConfiguration().setUserTaskDurationTime(userTaskDurationTime);
    return reportData;
  }

  public static ProcessReportDataDto createUserTaskIdleDurationGroupByAssigneeByUserTaskReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByAssigneeByUserTaskReport(aggroType, UserTaskDurationTime.IDLE);
  }

  public static ProcessReportDataDto createUserTaskTotalDurationGroupByAssigneeByUserTaskReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByAssigneeByUserTaskReport(aggroType, UserTaskDurationTime.TOTAL);
  }

  public static ProcessReportDataDto createUserTaskWorkDurationGroupByAssigneeByUserTaskReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByAssigneeByUserTaskReport(aggroType, UserTaskDurationTime.WORK);
  }

  private static ProcessReportDataDto createUserTaskDurationGroupByAssigneeByUserTaskReport(final AggregationType aggroType,
                                                                                            final UserTaskDurationTime userTaskDurationTime) {
    final ProcessViewDto view = createUserTaskDurationView();
    final ProcessGroupByDto groupByDto = createGroupByAssignee();

    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getConfiguration().setAggregationType(aggroType);
    reportData.getConfiguration().setUserTaskDurationTime(userTaskDurationTime);
    reportData.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
    return reportData;
  }

  public static ProcessReportDataDto createUserTaskFrequencyGroupByCandidateGroupReport() {
    final ProcessViewDto view = createUserTaskFrequencyView();
    final ProcessGroupByDto groupByDto = createGroupByCandidateGroup();

    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ProcessReportDataDto createUserTaskFrequencyGroupByCandidateGroupByUserTaskReport() {
    final ProcessReportDataDto reportData = createUserTaskFrequencyGroupByCandidateGroupReport();
    reportData.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
    return reportData;
  }

  public static ProcessReportDataDto createUserTaskIdleDurationGroupByCandidateGroupReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByCandidateGroupReport(aggroType, UserTaskDurationTime.IDLE);
  }

  public static ProcessReportDataDto createUserTaskTotalDurationGroupByCandidateGroupReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByCandidateGroupReport(aggroType, UserTaskDurationTime.TOTAL);
  }

  public static ProcessReportDataDto createUserTaskWorkDurationGroupByCandidateGroupReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByCandidateGroupReport(aggroType, UserTaskDurationTime.WORK);
  }

  private static ProcessReportDataDto createUserTaskDurationGroupByCandidateGroupReport(final AggregationType aggroType,
                                                                                        final UserTaskDurationTime userTaskDurationTime) {
    final ProcessViewDto view = createUserTaskDurationView();
    final ProcessGroupByDto groupByDto = createGroupByCandidateGroup();

    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getConfiguration().setAggregationType(aggroType);
    reportData.getConfiguration().setUserTaskDurationTime(userTaskDurationTime);
    return reportData;
  }

  public static ProcessReportDataDto createUserTaskIdleDurationGroupByCandidateGroupByUserTaskReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByCandidateGroupByUserTaskReport(aggroType, UserTaskDurationTime.IDLE);
  }

  public static ProcessReportDataDto createUserTaskTotalDurationGroupByCandidateGroupByUserTaskReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByCandidateGroupByUserTaskReport(aggroType, UserTaskDurationTime.TOTAL);
  }

  public static ProcessReportDataDto createUserTaskWorkDurationGroupByCandidateGroupByUserTaskReport(AggregationType aggroType) {
    return createUserTaskDurationGroupByCandidateGroupByUserTaskReport(aggroType, UserTaskDurationTime.WORK);
  }

  private static ProcessReportDataDto createUserTaskDurationGroupByCandidateGroupByUserTaskReport(final AggregationType aggroType,
                                                                                                  final UserTaskDurationTime userTaskDurationTime) {
    final ProcessViewDto view = createUserTaskDurationView();
    final ProcessGroupByDto groupByDto = createGroupByCandidateGroup();

    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    reportData.getConfiguration().setAggregationType(aggroType);
    reportData.getConfiguration().setUserTaskDurationTime(userTaskDurationTime);
    reportData.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
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
