/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public class RollingDateFilterIT extends AbstractDateFilterIT {

  @Test
  public void testStartDateRollingLogic() {
    // given
    embeddedOptimizeExtension.reloadConfiguration();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    OffsetDateTime processInstanceStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();

    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());

    importAllEngineEntitiesFromScratch();

    LocalDateUtil.setCurrentTime(processInstanceStartTime);

    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result =
      createAndEvaluateReportWithStartDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        DateFilterUnit.DAYS,
        1L,
        false,
        DateFilterType.ROLLING
      );

    assertResults(processInstance, result, 1);

    // when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(2L));

    //token has to be refreshed, as the old one expired already after moving the date
    result = createAndEvaluateReportWithStartDateFilter(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      DateFilterUnit.DAYS,
      1L,
      true,
      DateFilterType.ROLLING
    );

    assertResults(processInstance, result, 0);
  }

  @Test
  public void testEndDateRollingLogic() {
    embeddedOptimizeExtension.reloadConfiguration();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());

    OffsetDateTime processInstanceEndTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getEndTime();

    importAllEngineEntitiesFromScratch();

    LocalDateUtil.setCurrentTime(processInstanceEndTime);

    //token has to be refreshed, as the old one expired already after moving the date
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result =
      createAndEvaluateReportWithRollingEndDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        DateFilterUnit.DAYS,
        true
      );

    assertResults(processInstance, result, 1);

    LocalDateUtil.setCurrentTime(processInstanceEndTime.plusDays(2L));

    //token has to be refreshed, as the old one expired already after moving the date
    result = createAndEvaluateReportWithRollingEndDateFilter(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      DateFilterUnit.DAYS,
      true
    );

    assertResults(processInstance, result, 0);
  }

  @Test
  public void resultLimited_onTooBroadRollingStartDateFilter() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.deployAndStartProcess(
      getSingleServiceTaskProcess());
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -1L, 2L);
    final ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 100L);

    final ProcessInstanceEngineDto processInstanceDto4 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), startDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto5 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), startDate, -2L, 2L);
    final ProcessInstanceEngineDto processInstanceDto6 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), startDate, -2L, 3L);
    final ProcessInstanceEngineDto processInstanceDto7 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), startDate, -2L, 4L);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    reportData.setFilter(
      ProcessFilterBuilder.filter().rollingStartDate().start(10L, DateFilterUnit.DAYS).add().buildList()
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(2);
    assertThat(result.getIsComplete()).isFalse();

    assertThat(resultData.get(0).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(1), ChronoUnit.DAYS));
    assertThat(resultData.get(0).getValue()).isNull();

    assertThat(resultData.get(1).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS));
    assertThat(resultData.get(1).getValue())
      .isEqualTo(1000.);
  }

  @Test
  public void resultLimited_onTooBroadRollingEndDateFilter() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.deployAndStartProcess(
      getSingleServiceTaskProcess());
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 0L);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -1L, 10L);
    final ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 10L);

    final ProcessInstanceEngineDto processInstanceDto4 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), startDate, -2L, 10L);
    final ProcessInstanceEngineDto processInstanceDto5 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), startDate, -2L, 10L);
    final ProcessInstanceEngineDto processInstanceDto6 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), startDate, -2L, 10L);
    final ProcessInstanceEngineDto processInstanceDto7 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), startDate, -2L, 10L);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .rollingEndDate()
        .start(10L, DateFilterUnit.DAYS)
        .add()
        .buildList()
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(2);
    assertThat(result.getIsComplete()).isFalse();

    assertThat(resultData.get(0).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(1), ChronoUnit.DAYS));
    assertThat(resultData.get(1).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS));
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
                           .rollingStartDate()
                           .start(1L, DateFilterUnit.DAYS)
                           .add()
                           .rollingEndDate()
                           .start(1L, DateFilterUnit.DAYS)
                           .add()
                           .buildList());
    ReportMapResultDto result = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isEmpty();
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

}
