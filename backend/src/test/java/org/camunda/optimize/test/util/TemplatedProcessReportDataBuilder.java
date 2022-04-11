/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TemplatedProcessReportDataBuilder {

  private ProcessReportDataType reportDataType;

  // we want a modifiable list here
  private List<ReportDataDefinitionDto> definitions = new ArrayList<>(
    Arrays.asList(new ReportDataDefinitionDto())
  );
  private String variableName;
  private VariableType variableType;
  private AggregateByDateUnit groupByDateInterval = AggregateByDateUnit.AUTOMATIC;
  private AggregateByDateUnit distributeByDateInterval;
  private AggregateByDateUnit groupByDateVariableUnit = new SingleReportConfigurationDto().getGroupByDateVariableUnit();
  private String startFlowNodeId;
  private String endFlowNodeId;
  private UserTaskDurationTime userTaskDurationTime = new SingleReportConfigurationDto().getUserTaskDurationTimes()
    .stream().findFirst().orElse(null);
  private ProcessVisualization visualization;

  private List<ProcessFilterDto<?>> filter = new ArrayList<>();

  public static TemplatedProcessReportDataBuilder createReportData() {
    return new TemplatedProcessReportDataBuilder();
  }

  public ProcessReportDataDto build() {
    ProcessReportDataDto reportData;
    switch (reportDataType) {
      case RAW_DATA:
        reportData = new ProcessReportDataBuilderHelper()
          .viewProperty(ViewProperty.RAW_DATA)
          .groupByType(ProcessGroupByType.NONE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_NONE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.NONE)
          .visualization(ProcessVisualization.NUMBER)
          .definitions(definitions)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_NONE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.NONE)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_NONE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.NONE)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_START_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_START_DATE_BY_VARIABLE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.VARIABLE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .variableName(variableName)
          .variableType(variableType)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_START_DATE_BY_VARIABLE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.VARIABLE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .variableName(variableName)
          .variableType(variableType)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_START_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_END_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_END_DATE_BY_VARIABLE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.VARIABLE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .variableName(variableName)
          .variableType(variableType)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_END_DATE_BY_VARIABLE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.VARIABLE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .variableName(variableName)
          .variableType(variableType)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_END_DATE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_END_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableName(variableName)
          .variableType(variableType)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableName(variableName)
          .variableType(variableType)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableName(variableName)
          .variableType(variableType)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE_BY_START_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.START_DATE)
          .distributeByDateInterval(distributeByDateInterval)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableName(variableName)
          .variableType(variableType)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE_BY_START_DATE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.START_DATE)
          .distributeByDateInterval(distributeByDateInterval)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableName(variableName)
          .variableType(variableType)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE_BY_END_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.END_DATE)
          .distributeByDateInterval(distributeByDateInterval)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableName(variableName)
          .variableType(variableType)
          .build();
        break;
      case PROC_INST_DUR_GROUP_BY_VARIABLE_BY_END_DATE_WITH_PART:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.END_DATE)
          .distributeByDateInterval(distributeByDateInterval)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableName(variableName)
          .variableType(variableType)
          .processPartStart(startFlowNodeId)
          .processPartEnd(endFlowNodeId)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_NONE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.NONE)
          .visualization(ProcessVisualization.NUMBER)
          .definitions(definitions)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_START_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .visualization(ProcessVisualization.TABLE)
          .groupByDateInterval(groupByDateInterval)
          .definitions(definitions)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_START_DATE_BY_VARIABLE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.VARIABLE)
          .visualization(ProcessVisualization.TABLE)
          .groupByDateInterval(groupByDateInterval)
          .definitions(definitions)
          .variableName(variableName)
          .variableType(variableType)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_START_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .groupByDateInterval(groupByDateInterval)
          .definitions(definitions)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_END_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .visualization(ProcessVisualization.TABLE)
          .groupByDateInterval(groupByDateInterval)
          .definitions(definitions)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_END_DATE_BY_VARIABLE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.VARIABLE)
          .visualization(ProcessVisualization.TABLE)
          .groupByDateInterval(groupByDateInterval)
          .definitions(definitions)
          .variableName(variableName)
          .variableType(variableType)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_END_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .groupByDateInterval(groupByDateInterval)
          .definitions(definitions)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_RUNNING_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.RUNNING_DATE)
          .visualization(ProcessVisualization.TABLE)
          .groupByDateInterval(groupByDateInterval)
          .definitions(definitions)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_RUNNING_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.RUNNING_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .groupByDateInterval(groupByDateInterval)
          .definitions(definitions)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_START_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_START_DATE_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.FLOW_NODE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_START_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_END_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_END_DATE_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.FLOW_NODE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_END_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.DURATION)
          .distributedByType(DistributedByType.NONE)
          .definitions(definitions)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.DURATION)
          .distributedByType(DistributedByType.FLOW_NODE)
          .definitions(definitions)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.DURATION)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_VARIABLE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.VARIABLE)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableType(variableType)
          .variableName(variableName)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_VARIABLE_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.FLOW_NODE)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableType(variableType)
          .variableName(variableName)
          .build();
        break;
      case FLOW_NODE_FREQ_GROUP_BY_VARIABLE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableType(variableType)
          .variableName(variableName)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_NONE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.NONE)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .build();
        break;
      case PROC_INST_PER_GROUP_BY_NONE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.PERCENTAGE)
          .groupByType(ProcessGroupByType.NONE)
          .distributedByType(DistributedByType.NONE)
          .definitions(definitions)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .definitions(definitions)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_START_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_END_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_START_DATE_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.FLOW_NODE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_END_DATE_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.FLOW_NODE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_START_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_END_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_VARIABLE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableType(variableType)
          .variableName(variableName)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_VARIABLE_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.FLOW_NODE)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableType(variableType)
          .variableName(variableName)
          .build();
        break;
      case FLOW_NODE_DUR_GROUP_BY_VARIABLE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.FLOW_NODE)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableType(variableType)
          .variableName(variableName)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_VARIABLE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.VARIABLE)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableType(variableType)
          .variableName(variableName)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_VARIABLE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableType(variableType)
          .variableName(variableName)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_VARIABLE_BY_START_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.START_DATE)
          .distributeByDateInterval(distributeByDateInterval)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableType(variableType)
          .variableName(variableName)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_VARIABLE_BY_END_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.VARIABLE)
          .distributedByType(DistributedByType.END_DATE)
          .distributeByDateInterval(distributeByDateInterval)
          .visualization(ProcessVisualization.HEAT)
          .definitions(definitions)
          .variableType(variableType)
          .variableName(variableName)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_DURATION:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.DURATION)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case PROC_INST_FREQ_GROUP_BY_DURATION_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.PROCESS_INSTANCE)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.DURATION)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.USER_TASKS)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.USER_TASKS)
          .distributedByType(DistributedByType.ASSIGNEE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_BY_CANDIDATE_GROUP:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.USER_TASKS)
          .distributedByType(DistributedByType.CANDIDATE_GROUP)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.USER_TASKS)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.USER_TASK)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.ASSIGNEE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.CANDIDATE_GROUP)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_END_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_END_DATE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.USER_TASK)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_END_DATE_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.ASSIGNEE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_END_DATE_BY_CANDIDATE_GROUP:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.CANDIDATE_GROUP)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_END_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.ASSIGNEE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_ASSIGNEE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.ASSIGNEE)
          .distributedByType(DistributedByType.USER_TASK)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_ASSIGNEE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.ASSIGNEE)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_CANDIDATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.CANDIDATE_GROUP)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_CANDIDATE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.CANDIDATE_GROUP)
          .distributedByType(DistributedByType.USER_TASK)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_CANDIDATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.CANDIDATE_GROUP)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.DURATION)
          .distributedByType(DistributedByType.NONE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.DURATION)
          .distributedByType(DistributedByType.USER_TASK)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.DURATION)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.USER_TASKS)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.USER_TASK)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.ASSIGNEE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.CANDIDATE_GROUP)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.START_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_END_DATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_END_DATE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.USER_TASK)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_END_DATE_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.ASSIGNEE)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_END_DATE_BY_CANDIDATE_GROUP:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.CANDIDATE_GROUP)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_END_DATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.END_DATE)
          .distributedByType(DistributedByType.PROCESS)
          .definitions(definitions)
          .groupByDateInterval(groupByDateInterval)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.USER_TASKS)
          .distributedByType(DistributedByType.ASSIGNEE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_BY_CANDIDATE_GROUP:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.USER_TASKS)
          .distributedByType(DistributedByType.CANDIDATE_GROUP)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_USER_TASK_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.USER_TASKS)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_ASSIGNEE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.ASSIGNEE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_ASSIGNEE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.ASSIGNEE)
          .distributedByType(DistributedByType.USER_TASK)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_ASSIGNEE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.ASSIGNEE)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_CANDIDATE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.CANDIDATE_GROUP)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_CANDIDATE_BY_USER_TASK:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.CANDIDATE_GROUP)
          .distributedByType(DistributedByType.USER_TASK)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case USER_TASK_DUR_GROUP_BY_CANDIDATE_BY_PROCESS:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.USER_TASK)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.CANDIDATE_GROUP)
          .distributedByType(DistributedByType.PROCESS)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case VARIABLE_AGGREGATION_GROUP_BY_NONE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.VARIABLE)
          .viewProperty(ViewProperty.VARIABLE(variableName, variableType))
          .groupByType(ProcessGroupByType.NONE)
          .distributedByType(DistributedByType.NONE)
          .visualization(ProcessVisualization.NUMBER)
          .definitions(definitions)
          .build();
        break;
      case INCIDENT_FREQ_GROUP_BY_NONE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.INCIDENT)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.NONE)
          .distributedByType(DistributedByType.NONE)
          .visualization(ProcessVisualization.NUMBER)
          .definitions(definitions)
          .build();
        break;
      case INCIDENT_FREQ_GROUP_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.INCIDENT)
          .viewProperty(ViewProperty.FREQUENCY)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .distributedByType(DistributedByType.NONE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      case INCIDENT_DUR_GROUP_BY_NONE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.INCIDENT)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.NONE)
          .distributedByType(DistributedByType.NONE)
          .visualization(ProcessVisualization.NUMBER)
          .definitions(definitions)
          .build();
        break;
      case INCIDENT_DUR_GROUP_BY_FLOW_NODE:
        reportData = new ProcessReportDataBuilderHelper()
          .viewEntity(ProcessViewEntity.INCIDENT)
          .viewProperty(ViewProperty.DURATION)
          .groupByType(ProcessGroupByType.FLOW_NODES)
          .distributedByType(DistributedByType.NONE)
          .visualization(ProcessVisualization.TABLE)
          .definitions(definitions)
          .build();
        break;
      default:
        String errorMessage = String.format("Unknown ProcessReportDataType: [%s]", reportDataType.name());
        throw new OptimizeRuntimeException(errorMessage);
    }
    reportData.setFilter(this.filter);
    reportData.setVisualization(visualization == null ? reportData.getVisualization() : visualization);
    reportData.getConfiguration().setUserTaskDurationTimes(userTaskDurationTime);
    reportData.getConfiguration().setGroupByDateVariableUnit(groupByDateVariableUnit);
    return reportData;
  }

  public TemplatedProcessReportDataBuilder setReportDataType(ProcessReportDataType reportDataType) {
    this.reportDataType = reportDataType;
    return this;
  }

  public TemplatedProcessReportDataBuilder definitions(List<ReportDataDefinitionDto> definitions) {
    this.definitions = definitions;
    return this;
  }

  public TemplatedProcessReportDataBuilder setProcessDefinitionKey(String processDefinitionKey) {
    this.definitions.get(0).setKey(processDefinitionKey);
    return this;
  }

  public TemplatedProcessReportDataBuilder setProcessDefinitionVersion(String processDefinitionVersion) {
    this.definitions.get(0).setVersion(processDefinitionVersion);
    return this;
  }

  public TemplatedProcessReportDataBuilder setProcessDefinitionVersions(List<String> processDefinitionVersions) {
    this.definitions.get(0).setVersions(processDefinitionVersions);
    return this;
  }

  public TemplatedProcessReportDataBuilder setTenantIds(List<String> tenantIds) {
    this.definitions.get(0).setTenantIds(tenantIds);
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

  public TemplatedProcessReportDataBuilder setGroupByDateInterval(AggregateByDateUnit groupByDateInterval) {
    this.groupByDateInterval = groupByDateInterval;
    return this;
  }

  public TemplatedProcessReportDataBuilder setDistributeByDateInterval(AggregateByDateUnit distributeByDateInterval) {
    this.distributeByDateInterval = distributeByDateInterval;
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

  public TemplatedProcessReportDataBuilder setFilter(ProcessFilterDto<?> newFilter) {
    this.filter = Collections.singletonList(newFilter);
    return this;
  }

  public TemplatedProcessReportDataBuilder setFilter(List<ProcessFilterDto<?>> newFilter) {
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

  public TemplatedProcessReportDataBuilder setGroupByDateVariableUnit(final AggregateByDateUnit groupByDateVariableUnit) {
    this.groupByDateVariableUnit = groupByDateVariableUnit;
    return this;
  }
}
