/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;

public class SuspendedInstancesOnlyFilterIT extends AbstractFilterIT {

  @Test
  public void suspendedInstancesOnlyFilter() {
    // given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());

    engineDatabaseExtension.changeProcessInstanceState(
      firstProcInst.getId(),
      SUSPENDED_STATE
    );
    engineDatabaseExtension.changeProcessInstanceState(
      secondProcInst.getId(),
      SUSPENDED_STATE
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportWithDefinition(userTaskProcess);
    reportData.setFilter(ProcessFilterBuilder.filter().suspendedInstancesOnly().add().buildList());
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = reportClient.evaluateRawReport(reportData)
      .getResult();

    // then
    assertThat(result.getData()).hasSize(2);
    List<String> resultProcDefIds = result.getData()
      .stream()
      .map(RawDataProcessInstanceDto::getProcessInstanceId)
      .collect(Collectors.toList());

    assertThat(resultProcDefIds).contains(firstProcInst.getId());
    assertThat(resultProcDefIds).contains(secondProcInst.getId());
  }
}
