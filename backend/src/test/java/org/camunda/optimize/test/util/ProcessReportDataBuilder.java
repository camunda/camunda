/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByEndDate;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByStartDate;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByVariable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByEndDateReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByEndDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskFrequencyMapGroupByAssigneeByUserTaskReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskFrequencyMapGroupByAssigneeReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskFrequencyMapGroupByCandidateGroupByUserTaskReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskFrequencyMapGroupByCandidateGroupReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskFrequencyMapGroupByUserTaskReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskIdleDurationMapGroupByAssigneeByUserTaskReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskIdleDurationMapGroupByAssigneeReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskIdleDurationMapGroupByCandidateGroupByUserTaskReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskIdleDurationMapGroupByCandidateGroupReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskIdleDurationMapGroupByUserTaskReport;

public class ProcessReportDataBuilder {

  private ProcessReportDataType reportDataType;

  private String processDefinitionKey;
  private List<String> processDefinitionVersions;
  private String variableName;
  private VariableType variableType;
  private GroupByDateUnit dateInterval;
  private String startFlowNodeId;
  private String endFlowNodeId;
  private UserTaskDurationTime userTaskDurationTime;
  private ProcessVisualization visualization;

  private List<ProcessFilterDto> filter = new ArrayList<>();

  public static ProcessReportDataBuilder createReportData() {
    return new ProcessReportDataBuilder();
  }

  public ProcessReportDataDto build() {
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    switch (reportDataType) {
      case RAW_DATA:
        reportData = createProcessReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersions);
        break;
      case PROC_INST_DUR_GROUP_BY_NONE:
        reportData = createProcessInstanceDurationGroupByNone(
          processDefinitionKey,
          processDefinitionVersions
        );
        break;
      case PROC_INST_DUR_GROUP_BY_NONE_WITH_PART:
        reportData = createProcessInstanceDurationGroupByNoneWithProcessPart(
          processDefinitionKey,
          processDefinitionVersions,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case PROC_INST_DUR_GROUP_BY_START_DATE:
        reportData = createProcessInstanceDurationGroupByStartDateReport(
          processDefinitionKey,
          processDefinitionVersions,
          dateInterval
        );
        break;
      case PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART:
        reportData = createProcessInstanceDurationGroupByStartDateWithProcessPartReport(
          processDefinitionKey,
          processDefinitionVersions,
          dateInterval,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case PROC_INST_DUR_GROUP_BY_END_DATE:
        reportData = createProcessInstanceDurationGroupByEndDateReport(
          processDefinitionKey,
          processDefinitionVersions,
          dateInterval
        );
        break;
      case PROC_INST_DUR_GROUP_BY_END_DATE_WITH_PART:
        reportData = createProcessInstanceDurationGroupByEndDateWithProcessPartReport(
          processDefinitionKey,
          processDefinitionVersions,
          dateInterval,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE:
        reportData = createProcessInstanceDurationGroupByVariable(
          processDefinitionKey,
          processDefinitionVersions,
          variableName,
          variableType
        );
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART:
        reportData = createProcessInstanceDurationGroupByVariableWithProcessPart(
          processDefinitionKey,
          processDefinitionVersions,
          variableName,
          variableType,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_NONE:
        reportData = createPiFrequencyCountGroupedByNone(
          processDefinitionKey,
          processDefinitionVersions
        );
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE:
        reportData = createCountProcessInstanceFrequencyGroupByStartDate(
          processDefinitionKey,
          processDefinitionVersions,
          dateInterval
        );
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE:
        reportData = createCountProcessInstanceFrequencyGroupByEndDate(
          processDefinitionKey,
          processDefinitionVersions,
          dateInterval
        );
        break;
      case COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE:
        reportData = createCountFlowNodeFrequencyGroupByFlowNode(
          processDefinitionKey,
          processDefinitionVersions
        );
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE:
        reportData = createCountProcessInstanceFrequencyGroupByVariable(
          processDefinitionKey,
          processDefinitionVersions,
          variableName,
          variableType
        );
        break;
      case FLOW_NODE_DUR_GROUP_BY_FLOW_NODE:
        reportData = createFlowNodeDurationGroupByFlowNodeHeatmapReport(
          processDefinitionKey,
          processDefinitionVersions
        );
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_FLOW_NODE:
        reportData = createUserTaskFrequencyMapGroupByUserTaskReport(
          processDefinitionKey,
          processDefinitionVersions
        );
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE:
        reportData = createUserTaskFrequencyMapGroupByAssigneeReport(
          processDefinitionKey,
          processDefinitionVersions
        );
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_BY_USER_TASK:
        reportData = createUserTaskFrequencyMapGroupByAssigneeByUserTaskReport(
          processDefinitionKey,
          processDefinitionVersions
        );
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE:
        reportData = createUserTaskFrequencyMapGroupByCandidateGroupReport(
          processDefinitionKey,
          processDefinitionVersions
        );
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_BY_USER_TASK:
        reportData = createUserTaskFrequencyMapGroupByCandidateGroupByUserTaskReport(
          processDefinitionKey,
          processDefinitionVersions
        );
        break;
      case USER_TASK_DURATION_GROUP_BY_FLOW_NODE:
        reportData = createUserTaskIdleDurationMapGroupByUserTaskReport(
          processDefinitionKey,
          processDefinitionVersions
        );
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
      case USER_TASK_DURATION_GROUP_BY_ASSIGNEE:
        reportData = createUserTaskIdleDurationMapGroupByAssigneeReport(
          processDefinitionKey,
          processDefinitionVersions
        );
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
      case USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_USER_TASK:
        reportData = createUserTaskIdleDurationMapGroupByAssigneeByUserTaskReport(
          processDefinitionKey,
          processDefinitionVersions
        );
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
      case USER_TASK_DURATION_GROUP_BY_CANDIDATE:
        reportData = createUserTaskIdleDurationMapGroupByCandidateGroupReport(
          processDefinitionKey,
          processDefinitionVersions
        );
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
      case USER_TASK_DURATION_GROUP_BY_CANDIDATE_BY_USER_TASK:
        reportData = createUserTaskIdleDurationMapGroupByCandidateGroupByUserTaskReport(
          processDefinitionKey,
          processDefinitionVersions
        );
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
    }
    reportData.setFilter(this.filter);
    reportData.setVisualization(visualization == null? reportData.getVisualization() : visualization);
    return reportData;
  }

  private void setUserTaskDurationTimeIfConfigured(ProcessReportDataDto reportData) {
    if (userTaskDurationTime != null) {
      reportData.getConfiguration().setUserTaskDurationTime(userTaskDurationTime);
    }
  }

  public ProcessReportDataBuilder setReportDataType(ProcessReportDataType reportDataType) {
    this.reportDataType = reportDataType;
    return this;
  }

  public ProcessReportDataBuilder setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public ProcessReportDataBuilder setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersions = Lists.newArrayList(processDefinitionVersion);
    return this;
  }

  public ProcessReportDataBuilder setProcessDefinitionVersions(List<String> processDefinitionVersions) {
    this.processDefinitionVersions = processDefinitionVersions;
    return this;
  }

  public ProcessReportDataBuilder setVariableName(String variableName) {
    this.variableName = variableName;
    return this;
  }

  public ProcessReportDataBuilder setVariableType(VariableType variableType) {
    this.variableType = variableType;
    return this;
  }

  public ProcessReportDataBuilder setDateInterval(GroupByDateUnit dateInterval) {
    this.dateInterval = dateInterval;
    return this;
  }

  public ProcessReportDataBuilder setStartFlowNodeId(String startFlowNodeId) {
    this.startFlowNodeId = startFlowNodeId;
    return this;
  }

  public ProcessReportDataBuilder setEndFlowNodeId(String endFlowNodeId) {
    this.endFlowNodeId = endFlowNodeId;
    return this;
  }

  public ProcessReportDataBuilder setFilter(ProcessFilterDto newFilter) {
    this.filter = Collections.singletonList(newFilter);
    return this;
  }

  public ProcessReportDataBuilder setFilter(List<ProcessFilterDto> newFilter) {
    this.filter = newFilter;
    return this;
  }

  public ProcessReportDataBuilder setUserTaskDurationTime(UserTaskDurationTime userTaskDurationTime) {
    this.userTaskDurationTime = userTaskDurationTime;
    return this;
  }

  public ProcessReportDataBuilder setVisualization(ProcessVisualization visualization) {
    this.visualization = visualization;
    return this;
  }
}
