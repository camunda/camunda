/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;


@RunWith(Parameterized.class)
public class RollingDateFilterUnitsIT extends AbstractRollingDateFilterIT {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {RelativeDateFilterUnit.DAYS, 1},
      {RelativeDateFilterUnit.MINUTES, 1},
      {RelativeDateFilterUnit.HOURS, 1},
      {RelativeDateFilterUnit.WEEKS, 1},
      {RelativeDateFilterUnit.MONTHS, 1}
    });
  }

  private RelativeDateFilterUnit unit;
  private int expectedPiCount;

  public RollingDateFilterUnitsIT(RelativeDateFilterUnit unit, int expectedPiCount) {
    this.unit = unit;
    this.expectedPiCount = expectedPiCount;
  }

  @Test
  public void rollingDateFilterInReport() {
    // given

    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    OffsetDateTime processInstanceStartTime =
      engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // the clock of the engine and the clock of the computer running
    // the tests might not be aligned. Therefore we want to simulate
    // that the process instance is not started later than now.
    LocalDateUtil.setCurrentTime(processInstanceStartTime);

    // when
    ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result =
      createAndEvaluateReportWithRollingStartDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        unit,
        true
      );


    //then
    assertResults(processInstance, result, expectedPiCount);
  }


}
