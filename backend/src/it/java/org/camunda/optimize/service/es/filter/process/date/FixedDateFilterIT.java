/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.filter.process.AbstractFilterIT;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public class FixedDateFilterIT extends AbstractFilterIT {

  @Test
  public void testGetHeatMapWithGteStartDateCriteria() {
    //given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime start = processInstance.getStartTime();

    //when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);

    List<ProcessFilterDto<?>> fixedStartDateFilter =
      ProcessFilterBuilder.filter()
        .fixedStartDate()
        .start(start.plus(1, ChronoUnit.DAYS))
        .end(OffsetDateTime.now())
        .add()
        .buildList();
    reportData.setFilter(fixedStartDateFilter);
    RawDataProcessReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    //then
    assertThat(result.getData()).isEmpty();

    //when
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(start).end(null).add().buildList());
    result = reportClient.evaluateRawReport(reportData).getResult();
    //then
    assertThat(result.getData()).hasSize(1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(start.minus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    result = reportClient.evaluateRawReport(reportData).getResult();
    //then
    assertThat(result.getData()).hasSize(1);
  }

  @Test
  public void testGetHeatMapWithLteStartDateCriteria() {
    //given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime start = processInstance.getStartTime();

    //when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.plus(1, ChronoUnit.DAYS))
                           .add()
                           .buildList());
    RawDataProcessReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    //then
    assertThat(result.getData()).hasSize(1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.plusDays(1))
                           .add()
                           .buildList());
    result = reportClient.evaluateRawReport(reportData).getResult();
    //then
    assertThat(result.getData()).hasSize(1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.minus(1, ChronoUnit.DAYS))
                           .add()
                           .buildList());
    result = reportClient.evaluateRawReport(reportData).getResult();
    //then
    assertThat(result.getData()).isEmpty();
  }

  @Test
  public void testGetHeatMapWithGteEndDateCriteria() {
    //given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime end = processInstance.getEndTime();

    //when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);

    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(null)
                           .end(end.plus(1, ChronoUnit.DAYS))
                           .add()
                           .buildList());
    RawDataProcessReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    //then
    assertThat(result.getData()).hasSize(1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end)
                           .end(null)
                           .add()
                           .buildList());
    result = reportClient.evaluateRawReport(reportData).getResult();
    //then
    assertThat(result.getData()).hasSize(1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.plus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    result = reportClient.evaluateRawReport(reportData).getResult();
    //then
    assertThat(result.getData()).isEmpty();
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria() {
    //given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime end = processInstance.getEndTime();

    //when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.minus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    RawDataProcessReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    //then
    assertThat(result.getData()).hasSize(1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.plus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    result = reportClient.evaluateRawReport(reportData).getResult();
    //then
    assertThat(result.getData()).isEmpty();
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria() {
    //given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime start = processInstance.getStartTime();
    OffsetDateTime end = processInstance.getEndTime();

    ProcessReportDataDto reportData = createReportWithInstance(engineDto);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(start.minus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());

    //when
    RawDataProcessReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    //then
    assertThat(result.getData()).hasSize(1);

    //given
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.minusSeconds(200L))
                           .end(null)
                           .add()
                           .buildList());

    //when
    result = reportClient.evaluateRawReport(reportData).getResult();

    //then
    assertThat(result.getData()).hasSize(1);

    //given
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.minus(1, ChronoUnit.DAYS))
                           .add()
                           .buildList());

    //when
    result = reportClient.evaluateRawReport(reportData).getResult();

    //then
    assertThat(result.getData()).isEmpty();
  }

  @Test
  public void resultLimited_onTooBroadFixedStartDateFilter() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.parse("2019-06-15T12:00:00+01:00");
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.deployAndStartProcess(
      getSingleServiceTaskProcess());
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -1L, 2L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 100L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), startDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), startDate, -2L, 2L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), startDate, -2L, 3L);
    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), startDate, -2L, 4L);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .fixedStartDate()
        .start(startDate.minus(1, ChronoUnit.YEARS))
        .end(startDate)
        .add()
        .buildList()
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(1);
    assertThat(result.getIsComplete()).isFalse();

    assertThat(resultData.get(0).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS));
  }

  @Test
  public void resultLimited_noUserDefinedFilter_defaultFilter_limitEndsAtLatestProcessInstanceStartDate() {
    // given
    final OffsetDateTime oldStartDate = OffsetDateTime.parse("2019-06-15T12:00:00+01:00").minusDays(10L);
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.deployAndStartProcess(
      getSingleServiceTaskProcess());
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), oldStartDate, 0, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), oldStartDate, -1L, 2L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), oldStartDate, -1L, 100L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), oldStartDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), oldStartDate, -2L, 2L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), oldStartDate, -2L, 3L);
    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), oldStartDate, -2L, 4L);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(2);
    assertThat(result.getIsComplete()).isFalse();

    assertThat(resultData.get(resultData.size() - 1).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(oldStartDate, ChronoUnit.DAYS));
  }

  @Test
  public void resultLimited_onTooBroadFixedEndDateFilter() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.parse("2019-06-15T00:00:00+02:00");
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.deployAndStartProcess(
      getSingleServiceTaskProcess());
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 0L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -1L, 0L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 0L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), startDate, -2L, 0L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), startDate, -2L, 0L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), startDate, -2L, 0L);
    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), startDate, -2L, 0L);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .fixedEndDate()
        .start(startDate.minusYears(1))
        .end(startDate)
        .add()
        .buildList()
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(1);
    assertThat(result.getIsComplete()).isFalse();

    assertThat(resultData.get(0).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS));
  }

  @Test
  public void resultLimited_onTooBroadFixedEndAndStartDateFilter_startDateFilterIsLimited() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.parse("2019-06-15T12:00:00+01:00");
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.deployAndStartProcess(
      getSingleServiceTaskProcess());
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -1L, 1L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 1L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), startDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), startDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), startDate, -2L, 1L);

    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtension.startProcessInstance(
      processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), startDate, -3L, 1L);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .fixedEndDate()
        .start(startDate.minus(10, ChronoUnit.DAYS))
        .end(startDate)
        .add()
        .fixedStartDate()
        .start(startDate.minus(5, ChronoUnit.DAYS))
        .end(startDate)
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
    assertThat(resultData.get(0).getValue()).isEqualTo(2.);
    assertThat(resultData.get(1).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS));
    assertThat(resultData.get(1).getValue()).isEqualTo(0.);
  }

  private ProcessInstanceEngineDto startAndImportSimpleProcess() {
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.deployAndStartProcess(
      getSingleServiceTaskProcess());
    importAllEngineEntitiesFromScratch();
    return processInstanceDto;
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
