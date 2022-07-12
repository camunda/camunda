/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CombinedFlowNodeByVariableReportIT extends AbstractProcessDefinitionIT {

  @Test
  public void combineFrequencyReports() {
    // given
    final ProcessInstanceEngineDto processInstanceDto1 =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("stringVar", "aStringValue"));
    final ProcessInstanceEngineDto processInstanceDto2 =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("stringVar", "aStringValue"));
    importAllEngineEntitiesFromScratch();

    // when
    final String combinedReportId = reportClient.createNewCombinedReport(
      createFrequencyStringVariableReport(processInstanceDto1),
      createFrequencyStringVariableReport(processInstanceDto2)
    );
    final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.<List<MapResultEntryDto>>evaluateCombinedReportById(combinedReportId).getResult();

    // then
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getData().values())
      .allSatisfy(singleResult -> {
        assertThat(singleResult.getResult().getFirstMeasureData())
          .hasSize(1)
          .extracting(MapResultEntryDto::getKey)
          .containsOnly("aStringValue");
        assertThat(singleResult.getResult().getFirstMeasureData())
          .extracting(MapResultEntryDto::getValue)
          .containsOnly(4.);
      });
  }

  @Test
  public void combineDurationReports() {
    // given
    final ProcessInstanceEngineDto processInstanceDto1 =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("stringVar", "aStringValue"));
    changeActivityDuration(processInstanceDto1, 10.);

    final ProcessInstanceEngineDto processInstanceDto2 =
      deployAndStartTwoServiceTaskProcessWithVariables(Collections.singletonMap("stringVar", "aStringValue"));
    changeActivityDuration(processInstanceDto2, 10.);
    importAllEngineEntitiesFromScratch();

    // when
    final String combinedReportId = reportClient.createNewCombinedReport(
      createDurationStringVariableReport(processInstanceDto1),
      createDurationStringVariableReport(processInstanceDto2)
    );
    final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.<List<MapResultEntryDto>>evaluateCombinedReportById(combinedReportId).getResult();

    // then
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getData().values())
      .allSatisfy(singleResult -> {
        assertThat(singleResult.getResult().getFirstMeasureData())
          .hasSize(1)
          .extracting(MapResultEntryDto::getKey)
          .containsOnly("aStringValue");
        assertThat(singleResult.getResult().getFirstMeasureData())
          .extracting(MapResultEntryDto::getValue)
          .containsOnly(10.);
      });
  }

  private String createFrequencyStringVariableReport(final ProcessInstanceEngineDto processInstanceDto) {
    return createStringVariableReport(processInstanceDto, ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_VARIABLE);
  }

  private String createDurationStringVariableReport(final ProcessInstanceEngineDto processInstanceDto) {
    return createStringVariableReport(processInstanceDto, ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_VARIABLE);
  }

  private String createStringVariableReport(final ProcessInstanceEngineDto processInstanceDto,
                                            final ProcessReportDataType reportDataType) {
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setTenantIds(Collections.singletonList(null))
      .setVariableName("stringVar")
      .setVariableType(VariableType.STRING)
      .setReportDataType(reportDataType)
      .build();
    return createNewReport(reportData);
  }

}
