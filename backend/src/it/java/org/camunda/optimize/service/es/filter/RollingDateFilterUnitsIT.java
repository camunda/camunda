/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.stream.Stream;

public class RollingDateFilterUnitsIT extends AbstractRollingDateFilterIT {

  @ParameterizedTest
  @MethodSource("getData")
  public void rollingDateFilterInReport(RelativeDateFilterUnit relativeDateFilterUnit, int amount) {
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
      createAndEvaluateReportWithRollingStartDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        relativeDateFilterUnit,
        true
      );

    //then
    assertResults(processInstance, result, amount);
  }

  private static Stream<Arguments> getData() {
    return Stream.of(
      Arguments.of(RelativeDateFilterUnit.DAYS, 1),
      Arguments.of(RelativeDateFilterUnit.MINUTES, 1),
      Arguments.of(RelativeDateFilterUnit.HOURS, 1),
      Arguments.of(RelativeDateFilterUnit.WEEKS, 1),
      Arguments.of(RelativeDateFilterUnit.MONTHS, 1)
    );
  }

}
