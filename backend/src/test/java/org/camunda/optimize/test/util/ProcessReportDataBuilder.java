/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByStartDate;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByVariable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;

public class ProcessReportDataBuilder {

  private ProcessReportDataType reportDataType;

  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String variableName;
  private VariableType variableType;
  private GroupByDateUnit dateInterval;
  private String startFlowNodeId;
  private String endFlowNodeId;

  private List<ProcessFilterDto> filter = new ArrayList<>();

  public static ProcessReportDataBuilder createReportData() {
    return new ProcessReportDataBuilder();
  }

  public ProcessReportDataDto build() {
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    switch (reportDataType) {
      case RAW_DATA:
        reportData = createProcessReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
        break;
      case PROC_INST_DUR_GROUP_BY_NONE:
        reportData = createProcessInstanceDurationGroupByNone(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case PROC_INST_DUR_GROUP_BY_NONE_WITH_PART:
        reportData = createProcessInstanceDurationGroupByNoneWithProcessPart(
          processDefinitionKey,
          processDefinitionVersion,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case PROC_INST_DUR_GROUP_BY_START_DATE:
        reportData = createProcessInstanceDurationGroupByStartDateReport(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval
        );
        break;
      case PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART:
        reportData = createProcessInstanceDurationGroupByStartDateWithProcessPartReport(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE:
        reportData = createProcessInstanceDurationGroupByVariable(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType
        );
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART:
        reportData = createProcessInstanceDurationGroupByVariableWithProcessPart(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_NONE:
        reportData = createPiFrequencyCountGroupedByNone(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE:
        reportData = createCountProcessInstanceFrequencyGroupByStartDate(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval
        );
        break;
      case COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE:
        reportData = createCountFlowNodeFrequencyGroupByFlowNode(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE:
        reportData = createCountProcessInstanceFrequencyGroupByVariable(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType
        );
        break;
      case FLOW_NODE_DUR_GROUP_BY_FLOW_NODE:
        reportData = createFlowNodeDurationGroupByFlowNodeHeatmapReport(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
    }
    reportData.setFilter(this.filter);
    return reportData;
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
    this.processDefinitionVersion = processDefinitionVersion;
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
    this.filter.add(newFilter);
    return this;
  }

  public ProcessReportDataBuilder setFilter(List<ProcessFilterDto> newFilter) {
    this.filter.addAll(newFilter);
    return this;
  }
}
