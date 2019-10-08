/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByAssignee;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByCandidateGroup;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByEndDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByFlowNode;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByNone;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByStartDateDto;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessGroupByDtoCreator.createGroupByVariable;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createCountProcessInstanceFrequencyView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createUserTaskDurationView;
import static org.camunda.optimize.service.es.report.command.process.util.ProcessViewDtoCreator.createUserTaskFrequencyView;


public class ProcessReportDataBuilderHelper {

  public static ProcessReportDataDto createProcessReportDataViewRawAsTable(
    String processDefinitionKey,
    List<String> processDefinitionVersions
  ) {
    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      new ProcessViewDto(ProcessViewProperty.RAW_DATA),
      createGroupByNone()
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByStartDateReport(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    GroupByDateUnit dateInterval
  ) {
    return createProcessInstanceDurationGroupByDateReport(
      processDefinitionKey,
      processDefinitionVersions,
      createGroupByStartDateDto(dateInterval)
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByStartDateWithProcessPartReport(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    GroupByDateUnit dateInterval,
    String startFlowNodeId,
    String endFlowNodeId
  ) {
    return createProcessInstanceDurationGroupByDateWithProcessPartReport(
      processDefinitionKey,
      processDefinitionVersions,
      createGroupByStartDateDto(dateInterval),
      startFlowNodeId,
      endFlowNodeId
    );
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByStartDate(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    GroupByDateUnit dateInterval
  ) {
    return createCountProcessInstanceFrequencyGroupByDate(
      processDefinitionKey,
      processDefinitionVersions,
      createGroupByStartDateDto(dateInterval)
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByEndDateReport(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    GroupByDateUnit dateInterval
  ) {
    return createProcessInstanceDurationGroupByDateReport(
      processDefinitionKey,
      processDefinitionVersions,
      createGroupByEndDateDto(dateInterval)
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByEndDateWithProcessPartReport(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    GroupByDateUnit dateInterval,
    String startFlowNodeId,
    String endFlowNodeId
  ) {
    return createProcessInstanceDurationGroupByDateWithProcessPartReport(
      processDefinitionKey,
      processDefinitionVersions,
      createGroupByEndDateDto(dateInterval),
      startFlowNodeId,
      endFlowNodeId
    );
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByEndDate(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    GroupByDateUnit dateInterval
  ) {
    return createCountProcessInstanceFrequencyGroupByDate(
      processDefinitionKey,
      processDefinitionVersions,
      createGroupByEndDateDto(dateInterval)
    );
  }

  private static ProcessReportDataDto createProcessInstanceDurationGroupByDateReport(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    ProcessGroupByDto groupByDto
  ) {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
  }

  private static ProcessReportDataDto createProcessInstanceDurationGroupByDateWithProcessPartReport(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    ProcessGroupByDto groupByDto,
    String startFlowNodeId,
    String endFlowNodeId
  ) {
    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto,
      processPartDto
    );
  }

  private static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByDate(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    ProcessGroupByDto groupByDto
  ) {

    ProcessViewDto view = createCountProcessInstanceFrequencyView();

    ProcessReportDataDto reportData = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );

    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  private static ProcessReportDataDto createReportDataViewRaw(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    ProcessVisualization visualization,
    ProcessViewDto viewDto,
    ProcessGroupByDto groupByDto
  ) {
    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      visualization,
      viewDto,
      groupByDto,
      null
    );
  }

  private static ProcessReportDataDto createReportDataViewRaw(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    ProcessVisualization visualization,
    ProcessViewDto viewDto,
    ProcessGroupByDto groupByDto,
    ProcessPartDto processPartDto
  ) {
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey(processDefinitionKey);
    reportData.setProcessDefinitionVersions(processDefinitionVersions);
    reportData.setVisualization(visualization);
    reportData.setView(viewDto);
    reportData.setGroupBy(groupByDto);
    reportData.getConfiguration().setProcessPart(processPartDto);
    return reportData;
  }

  public static ProcessReportDataDto createCountFlowNodeFrequencyGroupByFlowNode(
    String processDefinitionKey,
    List<String> processDefinitionVersions
  ) {

    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.FREQUENCY);


    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.HEAT,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByVariable(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    String variableName,
    VariableType variableType
  ) {

    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.HEAT,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByVariable(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    String variableName,
    VariableType variableType
  ) {

    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.HEAT,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByVariableWithProcessPart(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    String variableName,
    VariableType variableType,
    String startFlowNodeId,
    String endFlowNodeId
  ) {
    ProcessReportDataDto reportData =
      createProcessInstanceDurationGroupByVariable(
        processDefinitionKey,
        processDefinitionVersions,
        variableName,
        variableType
      );
    reportData.getConfiguration().setProcessPart(createProcessPart(startFlowNodeId, endFlowNodeId));
    return reportData;
  }

  public static ProcessReportDataDto createFlowNodeDurationGroupByFlowNodeHeatmapReport(
    String processDefinitionKey,
    List<String> processDefinitionVersions
  ) {
    ProcessViewDto view = ProcessViewDtoCreator.createFlowNodeDurationView();

    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.HEAT,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createUserTaskFrequencyMapGroupByUserTaskReport(
    final String processDefinitionKey,
    final List<String> processDefinitionVersions
  ) {
    final ProcessViewDto view = createUserTaskFrequencyView();
    final ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createUserTaskFrequencyMapGroupByAssigneeReport(
    final String processDefinitionKey,
    final List<String> processDefinitionVersions
  ) {
    final ProcessViewDto view = createUserTaskFrequencyView();
    final ProcessGroupByDto groupByDto = createGroupByAssignee();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createUserTaskFrequencyMapGroupByAssigneeByUserTaskReport(
    final String processDefinitionKey,
    final List<String> processDefinitionVersions
  ) {
    final ProcessViewDto view = createUserTaskFrequencyView();
    final ProcessGroupByDto groupByDto = createGroupByAssignee();

    ProcessReportDataDto report = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
    report.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
    return report;
  }

  public static ProcessReportDataDto createUserTaskFrequencyMapGroupByCandidateGroupByUserTaskReport(
    final String processDefinitionKey,
    final List<String> processDefinitionVersions
  ) {
    final ProcessViewDto view = createUserTaskFrequencyView();
    final ProcessGroupByDto groupByDto = createGroupByCandidateGroup();

    ProcessReportDataDto report = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
    report.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);
    return report;
  }

  public static ProcessReportDataDto createUserTaskFrequencyMapGroupByCandidateGroupReport(
    final String processDefinitionKey,
    final List<String> processDefinitionVersions
  ) {
    final ProcessViewDto view = createUserTaskFrequencyView();
    final ProcessGroupByDto groupByDto = createGroupByCandidateGroup();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createUserTaskIdleDurationMapGroupByUserTaskReport(
    final String processDefinitionKey,
    final List<String> processDefinitionVersions
  ) {
    final ProcessViewDto view = createUserTaskDurationView();
    final ProcessGroupByDto groupByDto = createGroupByFlowNode();

    ProcessReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
    reportDataViewRaw.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);

    return reportDataViewRaw;
  }

  public static ProcessReportDataDto createUserTaskIdleDurationMapGroupByAssigneeReport(
    final String processDefinitionKey,
    final List<String> processDefinitionVersions
  ) {
    final ProcessViewDto view = createUserTaskDurationView();
    final ProcessGroupByDto groupByDto = createGroupByAssignee();

    ProcessReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
    reportDataViewRaw.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);

    return reportDataViewRaw;
  }

  public static ProcessReportDataDto createUserTaskIdleDurationMapGroupByAssigneeByUserTaskReport(
    final String processDefinitionKey,
    final List<String> processDefinitionVersions
  ) {
    final ProcessViewDto view = createUserTaskDurationView();
    final ProcessGroupByDto groupByDto = createGroupByAssignee();

    ProcessReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
    reportDataViewRaw.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);
    reportDataViewRaw.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);

    return reportDataViewRaw;
  }

  public static ProcessReportDataDto createUserTaskIdleDurationMapGroupByCandidateGroupByUserTaskReport(
    final String processDefinitionKey,
    final List<String> processDefinitionVersions
  ) {
    final ProcessViewDto view = createUserTaskDurationView();
    final ProcessGroupByDto groupByDto = createGroupByCandidateGroup();

    ProcessReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
    reportDataViewRaw.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);
    reportDataViewRaw.getConfiguration().setDistributedBy(DistributedBy.USER_TASK);

    return reportDataViewRaw;
  }

  public static ProcessReportDataDto createUserTaskIdleDurationMapGroupByCandidateGroupReport(
    final String processDefinitionKey,
    final List<String> processDefinitionVersions
  ) {
    final ProcessViewDto view = createUserTaskDurationView();
    final ProcessGroupByDto groupByDto = createGroupByCandidateGroup();

    ProcessReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.TABLE,
      view,
      groupByDto
    );
    reportDataViewRaw.getConfiguration().setUserTaskDurationTime(UserTaskDurationTime.IDLE);

    return reportDataViewRaw;
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByNone(
    String processDefinitionKey,
    List<String> processDefinitionVersions
  ) {

    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.NUMBER,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createProcessInstanceDurationGroupByNoneWithProcessPart(
    String processDefinitionKey,
    List<String> processDefinitionVersions,
    String startFlowNodeId,
    String endFlowNodeId
  ) {

    ProcessViewDto view = ProcessViewDtoCreator.createProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.HEAT,
      view,
      groupByDto,
      processPartDto
    );
  }

  public static ProcessReportDataDto createPiFrequencyCountGroupedByNone(
    String processDefinitionKey,
    List<String> processDefinitionVersions
  ) {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersions,
      ProcessVisualization.NUMBER,
      view,
      groupByDto
    );
  }

  public static CombinedReportDataDto createCombinedReport(String... reportIds) {
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
