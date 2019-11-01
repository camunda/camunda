/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;

import static org.camunda.optimize.test.util.ProcessReportDataType.RAW_DATA;

public abstract class AbstractFilterIT extends AbstractIT {

  protected static final String TEST_DEFINITION = "TestDefinition";
  protected final static String USER_TASK_ACTIVITY_ID = "User-Task";
  protected final static String USER_TASK_ACTIVITY_ID_2 = "User-Task2";
  protected final static String END_EVENT_ACTIVITY_ID = "endEvent";

  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension = new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  protected ProcessReportDataDto createReportWithInstance(ProcessInstanceEngineDto processInstanceEngineDto) {
    return createReport(processInstanceEngineDto.getProcessDefinitionKey(), processInstanceEngineDto.getProcessDefinitionVersion());
  }

  protected ProcessReportDataDto createReportWithDefinition(ProcessDefinitionEngineDto processDefinitionEngineDto) {
    return createReport(processDefinitionEngineDto.getKey(), processDefinitionEngineDto.getVersionAsString());
  }

  protected ProcessReportDataDto createReport(String definitionKey, String definitionVersion) {
    return ProcessReportDataBuilder
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

  protected Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  protected RawDataProcessReportResultDto evaluateReportAndReturnResult(final ProcessReportDataDto reportData) {
    return evaluateReportWithRawDataResult(reportData).getResult();
  }

  protected AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReportWithRawDataResult(
    final ProcessReportDataDto reportData) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

  protected AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluateReportWithMapResult(
    final ProcessReportDataDto reportData) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {});
      // @formatter:on
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

}
