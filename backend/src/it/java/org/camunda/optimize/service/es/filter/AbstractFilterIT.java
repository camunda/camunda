/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.camunda.optimize.test.util.ProcessReportDataType.RAW_DATA;

public abstract class AbstractFilterIT extends AbstractIT {

  protected static final String TEST_DEFINITION = "TestDefinition";
  protected final static String USER_TASK_ACTIVITY_ID = "User-Task";
  protected final static String USER_TASK_ACTIVITY_ID_2 = "User-Task2";
  protected final static String END_EVENT_ACTIVITY_ID = "endEvent";

  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension =
    new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

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
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }

  protected ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  protected ProcessDefinitionEngineDto deployTwoUserTasksProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent()
      .userTask(USER_TASK_ACTIVITY_ID)
      .userTask(USER_TASK_ACTIVITY_ID_2)
      .endEvent(END_EVENT_ACTIVITY_ID)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
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
}
