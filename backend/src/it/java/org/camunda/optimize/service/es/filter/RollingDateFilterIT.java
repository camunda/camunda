/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.DateFilterUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;

public class RollingDateFilterIT extends AbstractDateFilterIT {

  @ParameterizedTest
  @MethodSource("getRollingSupportedFilterUnits")
  public void testStartDateCurrentIntervalRollingLogic(DateFilterUnit dateFilterUnit) {
    // given
    embeddedOptimizeExtension.reloadConfiguration();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    OffsetDateTime processInstanceStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();

    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    LocalDateUtil.setCurrentTime(processInstanceStartTime);

    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result =
      createAndEvaluateReportWithStartDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        dateFilterUnit,
        0L,
        false,
        DateFilterType.ROLLING
      );

    assertResults(processInstance, result, 1);

    //when
    if (dateFilterUnit.equals(DateFilterUnit.QUARTERS)) {
      LocalDateUtil.setCurrentTime(OffsetDateTime.now().plus(3 * 2L, DateFilterUtil.unitOf(DateFilterUnit.MONTHS.getId())));
    } else {
      LocalDateUtil.setCurrentTime(OffsetDateTime.now().plus(2L, DateFilterUtil.unitOf(dateFilterUnit.getId())));
    }

    //token has to be refreshed, as the old one expired already after moving the date
    result = createAndEvaluateReportWithStartDateFilter(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      dateFilterUnit,
      0L,
      true,
      DateFilterType.ROLLING
    );

    assertResults(processInstance, result, 0);
  }

}
