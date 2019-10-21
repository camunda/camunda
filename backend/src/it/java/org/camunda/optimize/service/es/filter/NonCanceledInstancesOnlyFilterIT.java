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
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class NonCanceledInstancesOnlyFilterIT extends AbstractFilterIT {

  @Test
  public void nonCanceledInstancesFilter() throws SQLException {
    //given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto thirdProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());

    engineDatabaseExtensionRule.changeProcessInstanceState(
      firstProcInst.getId(),
      CanceledInstancesOnlyQueryFilter.INTERNALLY_TERMINATED
    );

    engineDatabaseExtensionRule.changeProcessInstanceState(
      thirdProcInst.getId(),
      CanceledInstancesOnlyQueryFilter.EXTERNALLY_TERMINATED
    );

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReportWithDefinition(userTaskProcess);
    reportData.setFilter(ProcessFilterBuilder.filter().nonCanceledInstancesOnly().add().buildList());
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertThat(result.getData().size(), is(1));
    List<String> resultProcDefIds = result.getData()
      .stream()
      .map(RawDataProcessInstanceDto::getProcessInstanceId)
      .collect(Collectors.toList());

    assertThat(resultProcDefIds, hasItem(secondProcInst.getId()));
  }

 }
