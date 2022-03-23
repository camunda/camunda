/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class AbstractDurationFilterIT extends AbstractFilterIT {

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  private void adjustProcessInstanceDates(String processInstanceId,
                                          OffsetDateTime startDate,
                                          long daysToShift,
                                          long durationInSec) {
    OffsetDateTime shiftedStartDate = startDate.plusDays(daysToShift);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, shiftedStartDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(
      processInstanceId,
      shiftedStartDate.plusSeconds(durationInSec)
    );
  }


  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(getSimpleBpmnDiagram(), variables);
  }

  protected ProcessInstanceEngineDto deployWithTimeShift(long daysToShift, long durationInSec) {
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    adjustProcessInstanceDates(processInstance.getId(), startDate, daysToShift, durationInSec);
    importAllEngineEntitiesFromScratch();
    return processInstance;
  }

  protected void assertResult(ProcessInstanceEngineDto processInstance,
                              AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> evaluationResult) {
    final ProcessReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getProcessDefinitionKey()).isEqualTo(processInstance.getProcessDefinitionKey());
    assertThat(resultDataDto.getDefinitionVersions()).containsExactly(processInstance.getProcessDefinitionVersion());
    assertThat(resultDataDto.getView()).isNotNull();
    final List<RawDataProcessInstanceDto> resultData = evaluationResult.getResult().getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    final RawDataProcessInstanceDto rawDataProcessInstanceDto = resultData.get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId()).isEqualTo(processInstance.getId());
  }
}
