/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.apache.http.HttpStatus;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DateFilterUnitsIT extends AbstractDateFilterIT {

  @ParameterizedTest
  @MethodSource("getRelativeSupportedFilterUnits")
  public void relativeDateFilterInReport(DateFilterUnit dateFilterUnit) {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    OffsetDateTime processInstanceStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // the clock of the engine and the clock of the computer running
    // the tests might not be aligned. Therefore we want to simulate
    // that the process instance is not started later than now.
    LocalDateUtil.setCurrentTime(processInstanceStartTime);

    // when
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result =
      createAndEvaluateReportWithStartDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        dateFilterUnit,
        1L,
        true,
        DateFilterType.RELATIVE
      );

    //then
    assertResults(processInstance, result, 1);
  }

  @Test
  public void relativeDateFilterInReport_unsupportedUnitQuarters() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processInstance.getProcessDefinitionKey(),
                                                   processInstance.getProcessDefinitionVersion());
    List<ProcessFilterDto> relativeDateFilter = createRelativeStartDateFilter(DateFilterUnit.QUARTERS, 1L);
    reportData.setFilter(relativeDateFilter);

    //then
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
  }

  @ParameterizedTest
  @MethodSource("getRollingSupportedFilterUnits")
  public void rollingDateFilterInReportCurrentInterval(DateFilterUnit dateFilterUnit) {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    OffsetDateTime processInstanceStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    LocalDateUtil.setCurrentTime(processInstanceStartTime);

    // when
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result =
      createAndEvaluateReportWithStartDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        dateFilterUnit,
        1L,
        true,
        DateFilterType.ROLLING
      );

    //then
    assertResults(processInstance, result, 0);
  }

  @ParameterizedTest
  @MethodSource("getRollingSupportedFilterUnits")
  public void rollingDateFilterInReportPreviousInterval(DateFilterUnit dateFilterUnit) {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    OffsetDateTime processInstanceStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    LocalDateUtil.setCurrentTime(processInstanceStartTime);

    // when
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result =
      createAndEvaluateReportWithStartDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        dateFilterUnit,
        0L,
        true,
        DateFilterType.ROLLING
      );

    //then
    assertResults(processInstance, result, 1);
  }

}
