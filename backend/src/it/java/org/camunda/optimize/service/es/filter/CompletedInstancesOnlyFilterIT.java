/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class CompletedInstancesOnlyFilterIT extends AbstractFilterIT {

  @Test
  public void filterByCompletedInstancesOnly() {
    // given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto thirdProcInst = engineIntegrationExtensionRule.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(firstProcInst.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(secondProcInst.getId());
    
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(userTaskProcess.getKey())
      .setProcessDefinitionVersion(userTaskProcess.getVersionAsString())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .setFilter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
      .build();
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    // then
    assertThat(result.getData().size(), is(2));
    assertThat(result.getData().get(0).getProcessInstanceId(), is(not(thirdProcInst.getId())));
    assertThat(result.getData().get(1).getProcessInstanceId(), is(not(thirdProcInst.getId())));
  }

}
