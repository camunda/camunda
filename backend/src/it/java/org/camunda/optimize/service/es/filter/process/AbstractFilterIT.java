/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;

import java.util.List;
import java.util.stream.Stream;

import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_RUNNING_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.RAW_DATA;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public abstract class AbstractFilterIT extends AbstractIT {

  protected static final String TEST_DEFINITION = "TestDefinition";

  protected ProcessReportDataDto createReportWithInstance(ProcessInstanceEngineDto processInstanceEngineDto) {
    return createReport(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion()
    );
  }

  protected ProcessReportDataDto createReportWithDefinition(ProcessDefinitionEngineDto processDefinitionEngineDto) {
    return createReport(processDefinitionEngineDto.getKey(), processDefinitionEngineDto.getVersionAsString());
  }

  protected ProcessReportDataDto createReport(String definitionKey, String definitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(RAW_DATA)
      .build();
  }

  protected ProcessDefinitionEngineDto deployUserTaskProcess() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram());
  }

  protected ProcessDefinitionEngineDto deployServiceTaskProcess() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleServiceTaskProcess());
  }

  protected ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram());
  }

  protected ProcessDefinitionEngineDto deployTwoUserTasksProcessDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram());
  }

  protected RawDataProcessReportResultDto evaluateReportWithFilter(final ProcessDefinitionEngineDto processDefinition,
                                                                   final List<ProcessFilterDto<?>> filterList) {
    return this.evaluateReportWithFilter(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion()),
      filterList
    );
  }

  protected RawDataProcessReportResultDto evaluateReportWithFilter(final String processDefinitionKey,
                                                                   final String processDefinitionVersion,
                                                                   final List<ProcessFilterDto<?>> filter) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .setFilter(filter)
      .build();
    return reportClient.evaluateRawReport(reportData).getResult();
  }

  protected ProcessReportDataDto getAutomaticGroupByDateReportData(final ProcessReportDataType type, final String key,
                                                                   final String version) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(key)
      .setProcessDefinitionVersion(version)
      .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .setReportDataType(type)
      .build();
  }

  @SuppressWarnings(UNUSED)
  private static Stream<ProcessReportDataType> simpleDateReportTypes() {
    return Stream.of(
      COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE,
      COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE,
      COUNT_PROC_INST_FREQ_GROUP_BY_RUNNING_DATE
    );
  }
}
