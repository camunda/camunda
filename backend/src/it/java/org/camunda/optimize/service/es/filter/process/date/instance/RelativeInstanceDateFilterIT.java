/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process.date.instance;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.DateFilterUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RelativeInstanceDateFilterIT extends AbstractInstanceDateFilterIT {

  @ParameterizedTest
  @MethodSource("getRelativeSupportedFilterUnits")
  public void testStartDateCurrentIntervalRelativeLogic(DateUnit dateUnit) {
    // given
    embeddedOptimizeExtension.reloadConfiguration();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    OffsetDateTime processInstanceStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();

    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());

    importAllEngineEntitiesFromScratch();

    LocalDateUtil.setCurrentTime(processInstanceStartTime);

    AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> result =
      createAndEvaluateReportWithStartDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        dateUnit,
        0L,
        false,
        DateFilterType.RELATIVE
      );

    assertResults(processInstance, result, 1);

    // when
    if (dateUnit.equals(DateUnit.QUARTERS)) {
      LocalDateUtil.setCurrentTime(OffsetDateTime.now()
                                     .plus(3 * 2L, DateFilterUtil.unitOf(DateUnit.MONTHS.getId())));
    } else {
      LocalDateUtil.setCurrentTime(OffsetDateTime.now().plus(2L, DateFilterUtil.unitOf(dateUnit.getId())));
    }

    //token has to be refreshed, as the old one expired already after moving the date
    result = createAndEvaluateReportWithStartDateFilter(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      dateUnit,
      0L,
      true,
      DateFilterType.RELATIVE
    );

    assertResults(processInstance, result, 0);
  }

  @ParameterizedTest
  @MethodSource("simpleDateReportTypes")
  public void dateReportsWithFilter_noDataReturnsEmptyResult(final ProcessReportDataType type) {
    // given
    ProcessDefinitionEngineDto engineDto = deployServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAutomaticGroupByDateReportData(
      type,
      engineDto.getKey(),
      engineDto.getVersionAsString()
    );
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .relativeInstanceStartDate()
                           .start(1L, DateUnit.DAYS)
                           .add()
                           .buildList());
    List<MapResultEntryDto> resultData = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then
    assertThat(resultData).isEmpty();
  }

}
