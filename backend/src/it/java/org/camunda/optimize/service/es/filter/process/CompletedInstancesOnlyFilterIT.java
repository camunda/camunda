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
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CompletedInstancesOnlyFilterIT extends AbstractFilterIT {

  @Test
  public void filterByCompletedInstancesOnly() {
    // given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    ProcessInstanceEngineDto firstProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto thirdProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstProcInst.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondProcInst.getId());
    
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(userTaskProcess.getKey())
      .setProcessDefinitionVersion(userTaskProcess.getVersionAsString())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .setFilter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList())
      .build();
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getProcessInstanceId)
      .doesNotContain(thirdProcInst.getId());
  }

}
