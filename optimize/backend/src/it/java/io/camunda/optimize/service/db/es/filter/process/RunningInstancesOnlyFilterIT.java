/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.filter.process;

import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import io.camunda.optimize.service.util.ProcessReportDataType;
import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class RunningInstancesOnlyFilterIT extends AbstractFilterIT {

  @Test
  public void filterByRunningInstancesOnly() {
    // given
    final ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
    final ProcessInstanceEngineDto firstProcInst =
        engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    final ProcessInstanceEngineDto secondProcInst =
        engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    final ProcessInstanceEngineDto thirdProcInst =
        engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(firstProcInst.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(secondProcInst.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
        TemplatedProcessReportDataBuilder.createReportData()
            .setProcessDefinitionKey(userTaskProcess.getKey())
            .setProcessDefinitionVersion(userTaskProcess.getVersionAsString())
            .setReportDataType(ProcessReportDataType.RAW_DATA)
            .build();
    reportData.setFilter(ProcessFilterBuilder.filter().runningInstancesOnly().add().buildList());
    final ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
        reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getData().get(0).getProcessInstanceId()).isEqualTo(thirdProcInst.getId());
  }
}
