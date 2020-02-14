/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TemplatedProcessReportDataBuilder {

  private ProcessReportDataType reportDataType;

  private String processDefinitionKey;
  private List<String> processDefinitionVersions;
  private List<String> tenantIds = Collections.singletonList(null);
  private String variableName;
  private VariableType variableType;
  private GroupByDateUnit dateInterval;
  private String startFlowNodeId;
  private String endFlowNodeId;
  private UserTaskDurationTime userTaskDurationTime;
  private ProcessVisualization visualization;

  private List<ProcessFilterDto> filter = new ArrayList<>();

  public static TemplatedProcessReportDataBuilder createReportData() {
    return new TemplatedProcessReportDataBuilder();
  }

  public ProcessReportDataDto build() {
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    switch (reportDataType) {
      case RAW_DATA:
        reportData = new ProcessReportDataBuilderHelper()
          .viewProperty(ProcessViewProperty.RAW_DATA)
          .groupByType(ProcessGroupByType.NONE)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_NONE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.NONE)
          .visualization(ProcessVisualization.NUMBER)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_NONE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.NONE)
          .visualization(ProcessVisualization.HEAT)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_START_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .dateInterval(dateInterval)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .dateInterval(dateInterval)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_END_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .dateInterval(dateInterval)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_END_DATE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .dateInterval(dateInterval)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .visualization(ProcessVisualization.HEAT)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .variableName(variableName)
          .variableType(variableType)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .visualization(ProcessVisualization.HEAT)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .variableName(variableName)
          .variableType(variableType)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_NONE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.NONE)
          .visualization(ProcessVisualization.NUMBER)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .visualization(ProcessVisualization.TABLE)
          .dateInterval(dateInterval)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .visualization(ProcessVisualization.TABLE)
          .dateInterval(dateInterval)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        break;
      case COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .visualization(ProcessVisualization.HEAT)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.VARIABLE)
          .visualization(ProcessVisualization.HEAT)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .variableType(variableType)
          .variableName(variableName)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_FLOW_NODE_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();

        reportData.getConfiguration().setDistributedBy(DistributedBy.ASSIGNEE);
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_FLOW_NODE_BY_CANDIDATE_GROUP:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();

        reportData.getConfiguration().setDistributedBy(DistributedBy.CANDIDATE_GROUP);
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.ASSIGNEE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.ASSIGNEE)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();

        reportData.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.CANDIDATE_GROUP)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        break;
      case USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.CANDIDATE_GROUP)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        reportData.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
        break;
      case USER_TASK_DURATION_GROUP_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        reportData.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
      case USER_TASK_DURATION_GROUP_BY_FLOW_NODE_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();

        reportData.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);
        reportData.getConfiguration().setDistributedBy(DistributedBy.ASSIGNEE);
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
      case USER_TASK_DURATION_GROUP_BY_FLOW_NODE_BY_CANDIDATE_GROUP:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();

        reportData.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);
        reportData.getConfiguration().setDistributedBy(DistributedBy.CANDIDATE_GROUP);
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
      case USER_TASK_DURATION_GROUP_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.ASSIGNEE)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();
        reportData.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
      case USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.ASSIGNEE)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionKey(processDefinitionKey)
          .processDefinitionVersions(processDefinitionVersions)
          .build();

        reportData.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);
        reportData.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
      case USER_TASK_DURATION_GROUP_BY_CANDIDATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.CANDIDATE_GROUP)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionVersions(processDefinitionVersions)
          .processDefinitionKey(processDefinitionKey)
          .build();

        reportData.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
      case USER_TASK_DURATION_GROUP_BY_CANDIDATE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ProcessViewProperty.DURATION)
          .groupByType(ProcessGroupByType.CANDIDATE_GROUP)
          .visualization(ProcessVisualization.TABLE)
          .processDefinitionVersions(processDefinitionVersions)
          .processDefinitionKey(processDefinitionKey)
          .build();

        reportData.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);
        reportData.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
        setUserTaskDurationTimeIfConfigured(reportData);
        break;
      default:
        String errorMessage = String.format("Unknown ProcessReportDataType: [%s]", reportDataType.name());
        throw new OptimizeRuntimeException(errorMessage);
    }
    reportData.setTenantIds(tenantIds);
    reportData.setFilter(this.filter);
    reportData.setVisualization(visualization == null ? reportData.getVisualization() : visualization);
    return reportData;
  }

  private void setUserTaskDurationTimeIfConfigured(ProcessReportDataDto reportData) {
    if (userTaskDurationTime != null) {
      reportData.getConfiguration().setUserTaskDurationTime(userTaskDurationTime);
    }
  }

  public TemplatedProcessReportDataBuilder setReportDataType(ProcessReportDataType reportDataType) {
    this.reportDataType = reportDataType;
    return this;
  }

  public TemplatedProcessReportDataBuilder setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public TemplatedProcessReportDataBuilder setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersions = Lists.newArrayList(processDefinitionVersion);
    return this;
  }

  public TemplatedProcessReportDataBuilder setProcessDefinitionVersions(List<String> processDefinitionVersions) {
    this.processDefinitionVersions = processDefinitionVersions;
    return this;
  }

  public TemplatedProcessReportDataBuilder setTenantIds(List<String> tenantIds) {
    this.tenantIds = tenantIds;
    return this;
  }

  public TemplatedProcessReportDataBuilder setVariableName(String variableName) {
    this.variableName = variableName;
    return this;
  }

  public TemplatedProcessReportDataBuilder setVariableType(VariableType variableType) {
    this.variableType = variableType;
    return this;
  }

  public TemplatedProcessReportDataBuilder setDateInterval(GroupByDateUnit dateInterval) {
    this.dateInterval = dateInterval;
    return this;
  }

  public TemplatedProcessReportDataBuilder setStartFlowNodeId(String startFlowNodeId) {
    this.startFlowNodeId = startFlowNodeId;
    return this;
  }

  public TemplatedProcessReportDataBuilder setEndFlowNodeId(String endFlowNodeId) {
    this.endFlowNodeId = endFlowNodeId;
    return this;
  }

  public TemplatedProcessReportDataBuilder setFilter(ProcessFilterDto newFilter) {
    this.filter = Collections.singletonList(newFilter);
    return this;
  }

  public TemplatedProcessReportDataBuilder setFilter(List<ProcessFilterDto> newFilter) {
    this.filter = newFilter;
    return this;
  }

  public TemplatedProcessReportDataBuilder setUserTaskDurationTime(UserTaskDurationTime userTaskDurationTime) {
    this.userTaskDurationTime = userTaskDurationTime;
    return this;
  }

  public TemplatedProcessReportDataBuilder setVisualization(ProcessVisualization visualization) {
    this.visualization = visualization;
    return this;
  }
}
