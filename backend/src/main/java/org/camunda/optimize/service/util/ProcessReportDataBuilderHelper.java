/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.process.util.ProcessDistributedByCreator.createDistributedByAssignee;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessDistributedByCreator.createDistributedByCandidateGroup;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessDistributedByCreator.createDistributedByEndDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessDistributedByCreator.createDistributedByFlowNode;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessDistributedByCreator.createDistributedByNone;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessDistributedByCreator.createDistributedByStartDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessDistributedByCreator.createDistributedByUserTasks;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessDistributedByCreator.createDistributedByVariable;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByAssignee;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByCandidateGroup;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByDuration;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByEndDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByFlowNode;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByNone;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByRunningDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByStartDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByUserTasks;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByVariable;

public class ProcessReportDataBuilderHelper {
  private List<ReportDataDefinitionDto> definitions = Collections.singletonList(new ReportDataDefinitionDto());

  private ProcessViewEntity viewEntity = null;
  private ViewProperty viewProperty = ViewProperty.RAW_DATA;
  private ProcessGroupByType groupByType = ProcessGroupByType.NONE;
  private DistributedByType distributedByType = DistributedByType.NONE;
  private ProcessVisualization visualization = ProcessVisualization.TABLE;
  private AggregateByDateUnit groupByDateInterval;
  private AggregateByDateUnit distributeByDateInterval;
  private String variableName;
  private VariableType variableType;
  private String processPartStart;
  private String processPartEnd;

  private ProcessPartDto processPart = null;

  public ProcessReportDataDto build() {
    final ProcessGroupByDto<?> groupBy = createGroupBy();
    final ProcessReportDistributedByDto<?> distributedBy = createDistributedBy();
    final ProcessViewDto view = new ProcessViewDto(viewEntity, viewProperty);
    if (processPartStart != null && processPartEnd != null) {
      processPart = createProcessPart(processPartStart, processPartEnd);
    }

    final ProcessReportDataDto reportData = ProcessReportDataDto.builder()
      .definitions(definitions)
      .visualization(visualization)
      .view(view)
      .groupBy(groupBy)
      .distributedBy(distributedBy)
      .build();
    reportData.getConfiguration().setProcessPart(processPart);
    return reportData;
  }

  private ProcessGroupByDto<?> createGroupBy() {
    switch (groupByType) {
      case NONE:
        return createGroupByNone();
      case VARIABLE:
        return createGroupByVariable(variableName, variableType);
      case START_DATE:
        return createGroupByStartDateDto(groupByDateInterval);
      case END_DATE:
        return createGroupByEndDateDto(groupByDateInterval);
      case RUNNING_DATE:
        return createGroupByRunningDateDto(groupByDateInterval);
      case ASSIGNEE:
        return createGroupByAssignee();
      case CANDIDATE_GROUP:
        return createGroupByCandidateGroup();
      case FLOW_NODES:
        return createGroupByFlowNode();
      case USER_TASKS:
        return createGroupByUserTasks();
      case DURATION:
        return createGroupByDuration();
      default:
        throw new OptimizeRuntimeException("Unsupported groupBy type:" + groupByType);
    }
  }

  private ProcessReportDistributedByDto<?> createDistributedBy() {
    switch (distributedByType) {
      case NONE:
        return createDistributedByNone();
      case ASSIGNEE:
        return createDistributedByAssignee();
      case CANDIDATE_GROUP:
        return createDistributedByCandidateGroup();
      case FLOW_NODE:
        return createDistributedByFlowNode();
      case USER_TASK:
        return createDistributedByUserTasks();
      case VARIABLE:
        return createDistributedByVariable(variableName, variableType);
      case START_DATE:
        return createDistributedByStartDateDto(distributeByDateInterval);
      case END_DATE:
        return createDistributedByEndDateDto(distributeByDateInterval);
      case PROCESS:
        return new ProcessDistributedByDto();
      default:
        throw new OptimizeRuntimeException("Unsupported distributedBy type:" + distributedByType);
    }
  }

  public ProcessReportDataBuilderHelper definitions(final List<ReportDataDefinitionDto> definitions) {
    this.definitions = definitions;
    return this;
  }

  public ProcessReportDataBuilderHelper processDefinitionKey(String processDefinitionKey) {
    this.definitions.get(0).setKey(processDefinitionKey);
    return this;
  }

  public ProcessReportDataBuilderHelper processDefinitionVersions(List<String> processDefinitionVersions) {
    this.definitions.get(0).setVersions(processDefinitionVersions);
    return this;
  }

  public ProcessReportDataBuilderHelper processDefinitionVersion(final String processDefinitionVersion) {
    this.definitions.get(0).setVersion(processDefinitionVersion);
    return this;
  }

  public ProcessReportDataBuilderHelper viewEntity(ProcessViewEntity viewEntity) {
    this.viewEntity = viewEntity;
    return this;
  }

  public ProcessReportDataBuilderHelper viewProperty(ViewProperty viewProperty) {
    this.viewProperty = viewProperty;
    return this;
  }

  public ProcessReportDataBuilderHelper groupByType(ProcessGroupByType groupByType) {
    this.groupByType = groupByType;
    return this;
  }

  public ProcessReportDataBuilderHelper distributedByType(DistributedByType distributedByType) {
    this.distributedByType = distributedByType;
    return this;
  }

  public ProcessReportDataBuilderHelper visualization(ProcessVisualization visualization) {
    this.visualization = visualization;
    return this;
  }

  public ProcessReportDataBuilderHelper groupByDateInterval(AggregateByDateUnit groupByDateInterval) {
    this.groupByDateInterval = groupByDateInterval;
    return this;
  }

  public ProcessReportDataBuilderHelper distributeByDateInterval(AggregateByDateUnit distributeByDateInterval) {
    this.distributeByDateInterval = distributeByDateInterval;
    return this;
  }

  public ProcessReportDataBuilderHelper variableName(String variableName) {
    this.variableName = variableName;
    return this;
  }

  public ProcessReportDataBuilderHelper variableType(VariableType variableType) {
    this.variableType = variableType;
    return this;
  }

  public ProcessReportDataBuilderHelper processPartStart(String processPartStart) {
    this.processPartStart = processPartStart;
    return this;
  }

  public ProcessReportDataBuilderHelper processPartEnd(String processPartEnd) {
    this.processPartEnd = processPartEnd;
    return this;
  }

  public static CombinedReportDataDto createCombinedReportData(String... reportIds) {
    CombinedReportDataDto combinedReportDataDto = new CombinedReportDataDto();
    combinedReportDataDto.setReports(
      Arrays.stream(reportIds).map(CombinedReportItemDto::new).collect(Collectors.toList())
    );
    return combinedReportDataDto;
  }

  private static ProcessPartDto createProcessPart(String start, String end) {
    ProcessPartDto processPartDto = new ProcessPartDto();
    processPartDto.setStart(start);
    processPartDto.setEnd(end);
    return processPartDto;
  }

}
