/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CanceledInstancesOnlyFilterIT extends AbstractFilterIT {

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
    ProcessReportDataDto reportData = createReportWithDefinition(userTaskProcess);
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
    ProcessReportDataDto reportData = createReportWithDefinition(userTaskProcess);
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
    ProcessReportDataDto reportData = createReportWithDefinition(userTaskProcess);
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

}
