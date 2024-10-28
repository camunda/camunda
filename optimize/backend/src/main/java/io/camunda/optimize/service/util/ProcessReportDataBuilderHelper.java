/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.service.db.report.interpreter.util.ProcessDistributedByCreator.createDistributedByAssignee;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessDistributedByCreator.createDistributedByCandidateGroup;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessDistributedByCreator.createDistributedByEndDateDto;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessDistributedByCreator.createDistributedByFlowNode;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessDistributedByCreator.createDistributedByNone;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessDistributedByCreator.createDistributedByStartDateDto;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessDistributedByCreator.createDistributedByUserTasks;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessDistributedByCreator.createDistributedByVariable;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessGroupByDtoCreator.createGroupByAssignee;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessGroupByDtoCreator.createGroupByCandidateGroup;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessGroupByDtoCreator.createGroupByDuration;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessGroupByDtoCreator.createGroupByEndDateDto;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessGroupByDtoCreator.createGroupByFlowNode;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessGroupByDtoCreator.createGroupByNone;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessGroupByDtoCreator.createGroupByRunningDateDto;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessGroupByDtoCreator.createGroupByStartDateDto;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessGroupByDtoCreator.createGroupByUserTasks;
import static io.camunda.optimize.service.db.report.interpreter.util.ProcessGroupByDtoCreator.createGroupByVariable;

import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessReportDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessReportDataBuilderHelper {
  private List<ReportDataDefinitionDto> definitions =
      Collections.singletonList(new ReportDataDefinitionDto());

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

    final ProcessReportDataDto reportData =
        ProcessReportDataDto.builder()
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

  public ProcessReportDataBuilderHelper definitions(
      final List<ReportDataDefinitionDto> definitions) {
    this.definitions = definitions;
    return this;
  }

  public ProcessReportDataBuilderHelper processDefinitionKey(final String processDefinitionKey) {
    this.definitions.get(0).setKey(processDefinitionKey);
    return this;
  }

  public ProcessReportDataBuilderHelper processDefinitionVersions(
      final List<String> processDefinitionVersions) {
    this.definitions.get(0).setVersions(processDefinitionVersions);
    return this;
  }

  public ProcessReportDataBuilderHelper processDefinitionVersion(
      final String processDefinitionVersion) {
    this.definitions.get(0).setVersion(processDefinitionVersion);
    return this;
  }

  public ProcessReportDataBuilderHelper viewEntity(final ProcessViewEntity viewEntity) {
    this.viewEntity = viewEntity;
    return this;
  }

  public ProcessReportDataBuilderHelper viewProperty(final ViewProperty viewProperty) {
    this.viewProperty = viewProperty;
    return this;
  }

  public ProcessReportDataBuilderHelper groupByType(final ProcessGroupByType groupByType) {
    this.groupByType = groupByType;
    return this;
  }

  public ProcessReportDataBuilderHelper distributedByType(
      final DistributedByType distributedByType) {
    this.distributedByType = distributedByType;
    return this;
  }

  public ProcessReportDataBuilderHelper visualization(final ProcessVisualization visualization) {
    this.visualization = visualization;
    return this;
  }

  public ProcessReportDataBuilderHelper groupByDateInterval(
      final AggregateByDateUnit groupByDateInterval) {
    this.groupByDateInterval = groupByDateInterval;
    return this;
  }

  public ProcessReportDataBuilderHelper distributeByDateInterval(
      final AggregateByDateUnit distributeByDateInterval) {
    this.distributeByDateInterval = distributeByDateInterval;
    return this;
  }

  public ProcessReportDataBuilderHelper variableName(final String variableName) {
    this.variableName = variableName;
    return this;
  }

  public ProcessReportDataBuilderHelper variableType(final VariableType variableType) {
    this.variableType = variableType;
    return this;
  }

  public ProcessReportDataBuilderHelper processPartStart(final String processPartStart) {
    this.processPartStart = processPartStart;
    return this;
  }

  public ProcessReportDataBuilderHelper processPartEnd(final String processPartEnd) {
    this.processPartEnd = processPartEnd;
    return this;
  }

  public static CombinedReportDataDto createCombinedReportData(final String... reportIds) {
    final CombinedReportDataDto combinedReportDataDto = new CombinedReportDataDto();
    combinedReportDataDto.setReports(
        Arrays.stream(reportIds).map(CombinedReportItemDto::new).collect(Collectors.toList()));
    return combinedReportDataDto;
  }

  private static ProcessPartDto createProcessPart(final String start, final String end) {
    final ProcessPartDto processPartDto = new ProcessPartDto();
    processPartDto.setStart(start);
    processPartDto.setEnd(end);
    return processPartDto;
  }
}
