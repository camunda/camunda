/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.INTERNALLY_TERMINATED_STATE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CanceledInstancesOnlyFilterIT extends AbstractFilterIT {

  @Test
  public void mixedCanceledInstancesOnlyFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());

    engineIntegrationExtension.externallyTerminateProcessInstance(firstProcInst.getId());
    engineDatabaseExtension.changeProcessInstanceState(
      secondProcInst.getId(),
      INTERNALLY_TERMINATED_STATE
    );


    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportWithDefinition(userTaskProcess);
    reportData.setFilter(ProcessFilterBuilder.filter().canceledInstancesOnly().add().buildList());
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData.size(), is(2));
    List<String> resultProcDefIds = resultData
      .stream()
      .map(RawDataProcessInstanceDto::getProcessInstanceId)
      .collect(Collectors.toList());

    assertThat(resultProcDefIds.contains(firstProcInst.getId()), is(true));
    assertThat(resultProcDefIds.contains(secondProcInst.getId()), is(true));
  }

  @Test
  public void internallyTerminatedCanceledInstancesOnlyFilter() throws Exception {
    // given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());

    engineDatabaseExtension.changeProcessInstanceState(
      firstProcInst.getId(),
      INTERNALLY_TERMINATED_STATE
    );
    engineDatabaseExtension.changeProcessInstanceState(
      secondProcInst.getId(),
      INTERNALLY_TERMINATED_STATE
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportWithDefinition(userTaskProcess);
    reportData.setFilter(ProcessFilterBuilder.filter().canceledInstancesOnly().add().buildList());
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData.size(), is(2));
    List<String> resultProcDefIds = resultData
      .stream()
      .map(RawDataProcessInstanceDto::getProcessInstanceId)
      .collect(Collectors.toList());

    assertThat(resultProcDefIds.contains(firstProcInst.getId()), is(true));
    assertThat(resultProcDefIds.contains(secondProcInst.getId()), is(true));
  }

  @Test
  public void externallyTerminatedCanceledInstncesOnlyFilter() {
    // given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());

    engineIntegrationExtension.externallyTerminateProcessInstance(firstProcInst.getId());
    engineIntegrationExtension.externallyTerminateProcessInstance(secondProcInst.getId());

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportWithDefinition(userTaskProcess);
    reportData.setFilter(ProcessFilterBuilder.filter().canceledInstancesOnly().add().buildList());
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData.size(), is(2));
    List<String> resultProcDefIds = resultData
      .stream()
      .map(RawDataProcessInstanceDto::getProcessInstanceId)
      .collect(Collectors.toList());

    assertThat(resultProcDefIds.contains(firstProcInst.getId()), is(true));
    assertThat(resultProcDefIds.contains(secondProcInst.getId()), is(true));
  }

}
