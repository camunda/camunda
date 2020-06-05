/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class DurationFilterParameterizedIT extends AbstractDurationFilterIT {

  @ParameterizedTest
  @MethodSource("getArguments")
  public void testGetReportWithLtDurationCriteria (boolean deployWithTimeShift,
                                                   Long daysToShift,
                                                   Long durationInSec,
                                                   String operator,
                                                   int duration,
                                                   DurationFilterUnit unit) throws Exception {
    // given
    ProcessInstanceEngineDto processInstance;
    if (deployWithTimeShift) {
      processInstance = deployWithTimeShift(daysToShift, durationInSec);
    } else {
      processInstance = deployAndStartSimpleProcess();
    }

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setFilter(ProcessFilterBuilder
                           .filter()
                           .duration()
                           .unit(unit)
                           .value((long) duration)
                           .operator(operator)
                           .add()
                           .buildList());
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result = reportClient.evaluateRawReport(
      reportData);

    // then
    assertResult(processInstance, result);
  }

  private static Stream<Arguments> getArguments() {
    return Stream.of(
      Arguments.of(false, null, null, "<", 1, DurationFilterUnit.SECONDS),
      Arguments.of(false, null, null, "<", 1, DurationFilterUnit.MINUTES),
      Arguments.of(false, null, null, "<", 1, DurationFilterUnit.HOURS),
      Arguments.of(false, null, null, "<", 1, DurationFilterUnit.HALF_DAYS),
      Arguments.of(false, null, null, "<", 1, DurationFilterUnit.DAYS),
      Arguments.of(false, null, null, "<", 1, DurationFilterUnit.WEEKS),
      Arguments.of(false, null, null, "<", 1, DurationFilterUnit.MONTHS),
      Arguments.of(true, 0L, 2L, ">", 1, DurationFilterUnit.SECONDS),
      Arguments.of(true, 0L, 2L, ">=", 2, DurationFilterUnit.SECONDS)
    );
  }

}
