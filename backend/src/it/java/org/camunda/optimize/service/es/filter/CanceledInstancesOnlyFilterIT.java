/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CanceledInstancesOnlyFilterIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();
  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtensionRule engineDatabaseExtensionRule = new EngineDatabaseExtensionRule(engineIntegrationExtensionRule.getEngineName());

  @Test
  public void mixedCanceledInstancesOnlyFilter() throws Exception {
    //given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());

    engineIntegrationExtensionRule.externallyTerminateProcessInstance(firstProcInst.getId());
    engineDatabaseExtensionRule.changeProcessInstanceState(
            secondProcInst.getId(),
            CanceledInstancesOnlyQueryFilter.INTERNALLY_TERMINATED
    );


    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(userTaskProcess);
    reportData.setFilter(ProcessFilterBuilder.filter().canceledInstancesOnly().add().buildList());
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    // then
    assertThat(result.getData().size(), is(2));
    List<String> resultProcDefIds = result.getData()
            .stream()
            .map(RawDataProcessInstanceDto::getProcessInstanceId)
            .collect(Collectors.toList());

    assertThat(resultProcDefIds.contains(firstProcInst.getId()), is(true));
    assertThat(resultProcDefIds.contains(secondProcInst.getId()), is(true));
  }

  @Test
  public void internallyTerminatedCanceledInstancesOnlyFilter() throws Exception {
    //given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());

    engineDatabaseExtensionRule.changeProcessInstanceState(
            firstProcInst.getId(),
            CanceledInstancesOnlyQueryFilter.INTERNALLY_TERMINATED
    );
    engineDatabaseExtensionRule.changeProcessInstanceState(
            secondProcInst.getId(),
            CanceledInstancesOnlyQueryFilter.INTERNALLY_TERMINATED
    );

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(userTaskProcess);
    reportData.setFilter(ProcessFilterBuilder.filter().canceledInstancesOnly().add().buildList());
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertThat(result.getData().size(), is(2));
    List<String> resultProcDefIds = result.getData()
            .stream()
            .map(RawDataProcessInstanceDto::getProcessInstanceId)
            .collect(Collectors.toList());

    assertThat(resultProcDefIds.contains(firstProcInst.getId()), is(true));
    assertThat(resultProcDefIds.contains(secondProcInst.getId()), is(true));
  }

  @Test
  public void externallyTerminatedCanceledInstncesOnlyFilter() {
    //given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());

    engineIntegrationExtensionRule.externallyTerminateProcessInstance(firstProcInst.getId());
    engineIntegrationExtensionRule.externallyTerminateProcessInstance(secondProcInst.getId());

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(userTaskProcess);
    reportData.setFilter(ProcessFilterBuilder.filter().canceledInstancesOnly().add().buildList());
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertThat(result.getData().size(), is(2));
    List<String> resultProcDefIds = result.getData()
            .stream()
            .map(RawDataProcessInstanceDto::getProcessInstanceId)
            .collect(Collectors.toList());

    assertThat(resultProcDefIds.contains(firstProcInst.getId()), is(true));
    assertThat(resultProcDefIds.contains(secondProcInst.getId()), is(true));
  }

  private RawDataProcessReportResultDto evaluateReportAndReturnResult(final ProcessReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {})
      // @formatter:on
      .getResult();
  }

  private ProcessDefinitionEngineDto deployUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
            .name("aProcessName")
            .startEvent()
            .userTask()
            .endEvent()
            .done();
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(processModel);
  }

  private ProcessReportDataDto createReport(ProcessDefinitionEngineDto userTaskProcess) {
    return ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(userTaskProcess.getKey())
      .setProcessDefinitionVersion(userTaskProcess.getVersionAsString())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
  }
}
