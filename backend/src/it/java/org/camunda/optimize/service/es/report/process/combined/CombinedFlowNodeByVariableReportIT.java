/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getTwoServiceTasksProcess;

public class CombinedFlowNodeByVariableReportIT extends AbstractProcessDefinitionIT {

  @Test
  public void combineFrequencyReports() {
    // given
    final ProcessInstanceEngineDto processInstanceDto1 = deployProcessWithFourFlowNodes(Collections.singletonMap(
      "stringVar",
      "aStringValue"
    ));
    final ProcessInstanceEngineDto processInstanceDto2 = deployProcessWithFourFlowNodes(Collections.singletonMap(
      "stringVar",
      "aStringValue"
    ));
    importAllEngineEntitiesFromScratch();

    // when
    final CombinedReportDefinitionRequestDto combinedReport = createCombinedReport(
      createFrequencyStringVariableReport(processInstanceDto1),
      createFrequencyStringVariableReport(processInstanceDto2)
    );
    final IdResponseDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.<ReportMapResultDto>evaluateCombinedReportById(response.getId()).getResult();

    // then
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getData().values())
      .allSatisfy(singleResult -> {
        assertThat(singleResult.getResult().getData())
          .hasSize(1)
          .extracting(MapResultEntryDto::getKey)
          .containsOnly("aStringValue");
        assertThat(singleResult.getResult().getData())
          .extracting(MapResultEntryDto::getValue)
          .containsOnly(4.);
      });
  }

  private ProcessInstanceEngineDto deployProcessWithFourFlowNodes(final Map<String, Object> variables) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      getTwoServiceTasksProcess("aProcess"),
      variables
    );
  }

  private ProcessReportDataDto createFrequencyStringVariableReport(final ProcessInstanceEngineDto processInstanceDto) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setTenantIds(Collections.singletonList(null))
      .setVariableName("stringVar")
      .setVariableType(VariableType.STRING)
      .setReportDataType(ProcessReportDataType.FLOW_NODE_FREQUENCY_GROUP_BY_VARIABLE)
      .build();
  }

  private CombinedReportDefinitionRequestDto createCombinedReport(final ProcessReportDataDto reportData1,
                                                                  final ProcessReportDataDto reportData2) {
    final String reportId1 = createNewReport(reportData1);
    final String reportId2 = createNewReport(reportData2);

    final CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
    final List<CombinedReportItemDto> reportIds = new ArrayList<>();
    reportIds.add(new CombinedReportItemDto(reportId1));
    reportIds.add(new CombinedReportItemDto(reportId2));

    combinedReportData.setReports(reportIds);
    final CombinedReportDefinitionRequestDto combinedReport = new CombinedReportDefinitionRequestDto();
    combinedReport.setData(combinedReportData);
    return combinedReport;
  }
}
