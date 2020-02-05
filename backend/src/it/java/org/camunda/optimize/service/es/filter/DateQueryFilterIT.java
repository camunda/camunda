/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DateQueryFilterIT extends AbstractFilterIT {

  private static final String TEST_ACTIVITY = "testActivity";
  private static final long TIME_OFFSET_MILLS = 2000L;

  @Test
  public void testGetHeatMapWithGteStartDateCriteria() {
    //given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance = engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime start = processInstance.getStartTime();

    //when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);

    List<ProcessFilterDto> fixedStartDateFilter =
      ProcessFilterBuilder.filter()
        .fixedStartDate()
        .start(start.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
        .end(OffsetDateTime.now())
        .add()
        .buildList();
    reportData.setFilter(fixedStartDateFilter);
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 0);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(start).end(null).add().buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .end(null)
                           .add()
                           .buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 1);
  }

  @Test
  public void testGetHeatMapWithLteStartDateCriteria() {
    //given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance = engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime start = processInstance.getStartTime();

    //when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .add()
                           .buildList());
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(null).end(start).add().buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .add()
                           .buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithGteEndDateCriteria() {
    //given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance = engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime end = processInstance.getEndTime();

    //when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);

    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(null)
                           .end(end.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .add()
                           .buildList());
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end)
                           .end(null)
                           .add()
                           .buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .end(null)
                           .add()
                           .buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria() {
    //given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance = engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime end = processInstance.getEndTime();

    //when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .end(null)
                           .add()
                           .buildList());
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 1);

    //when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.plus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .end(null)
                           .add()
                           .buildList());
    result = evaluateReportAndReturnResult(reportData);
    //then
    assertResults(result, 0);
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria() {
    //given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance = engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime start = processInstance.getStartTime();
    OffsetDateTime end = processInstance.getEndTime();

    ProcessReportDataDto reportData = createReportWithInstance(engineDto);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .end(null)
                           .add()
                           .buildList());

    //when
    RawDataProcessReportResultDto result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 1);

    //given
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.minusSeconds(200L))
                           .end(null)
                           .add()
                           .buildList());

    //when
    result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 1);

    //given
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.minus(TIME_OFFSET_MILLS, ChronoUnit.MILLIS))
                           .add()
                           .buildList());

    //when
    result = evaluateReportAndReturnResult(reportData);

    //then
    assertResults(result, 0);
  }

  @Test
  public void resultLimited_onTooBroadFixedStartDateFilter() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -1L, 2L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 100L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), startDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), startDate, -2L, 2L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), startDate, -2L, 3L);
    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), startDate, -2L, 4L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
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
    final ReportMapResultDto result = evaluateReportWithMapResult(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(1));
    assertThat(result.getIsComplete(), is(false));

    assertThat(
      resultData.get(0).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS))
    );
  }

  @Test
  public void resultLimited_noUserDefinedFilter_defaultFilter_limitEndsAtLatestProcessInstanceStartDate() {
    // given
    final OffsetDateTime oldStartDate = OffsetDateTime.now().minusDays(10L);
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), oldStartDate, 0, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), oldStartDate, -1L, 2L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), oldStartDate, -1L, 100L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), oldStartDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), oldStartDate, -2L, 2L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), oldStartDate, -2L, 3L);
    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), oldStartDate, -2L, 4L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    final ReportMapResultDto result = evaluateReportWithMapResult(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    assertThat(result.getIsComplete(), is(false));

    assertThat(
      resultData.get(0).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(oldStartDate, ChronoUnit.DAYS))
    );
  }

  @Test
  public void resultLimited_onTooBroadFixedEndDateFilter() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 0L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -1L, 0L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 0L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), startDate, -2L, 0L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), startDate, -2L, 0L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), startDate, -2L, 0L);
    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), startDate, -2L, 0L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .fixedEndDate()
        .start(startDate.minus(5, ChronoUnit.DAYS))
        .end(startDate)
        .add()
        .buildList()
    );
    final ReportMapResultDto result = evaluateReportWithMapResult(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    assertThat(result.getIsComplete(), is(false));

    assertThat(
      resultData.get(0).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS))
    );
    assertThat(
      resultData.get(1).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(1), ChronoUnit.DAYS))
    );
  }

  @Test
  public void resultLimited_onTooBroadFixedEndAndStartDateFilter_startDateFilterIsLimited() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -1L, 1L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 1L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), startDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), startDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), startDate, -2L, 1L);

    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), startDate, -3L, 1L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .fixedEndDate()
        .start(startDate.minus(10, ChronoUnit.DAYS).plusSeconds(1L))
        .end(startDate.minus(1L, ChronoUnit.DAYS).plusSeconds(1L))
        .add()
        .fixedStartDate()
        .start(startDate.minus(5, ChronoUnit.DAYS))
        .end(startDate)
        .add()
        .buildList()
    );
    final ReportMapResultDto result = evaluateReportWithMapResult(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    assertThat(result.getIsComplete(), is(false));

    assertThat(
      resultData.get(0).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS))
    );
    assertThat(resultData.get(0).getValue(), is(0L));
    assertThat(
      resultData.get(1).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(1), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(1).getValue(), is(2L));
  }

  private ProcessInstanceEngineDto startAndImportSimpleProcess() {
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return processInstanceDto;
  }

  private void adjustProcessInstanceDates(String processInstanceId,
                                          OffsetDateTime startDate,
                                          long daysToShift,
                                          long durationInSec) {
    OffsetDateTime shiftedStartDate = startDate.plusDays(daysToShift);
    try {
      engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, shiftedStartDate);
      engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceId, shiftedStartDate.plusSeconds(durationInSec));
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException("Failed adjusting process instance dates", e);
    }
  }

  private void assertResults(RawDataProcessReportResultDto resultMap, int size) {
    assertThat(resultMap.getData().size(), is(size));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String activityId) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask(activityId)
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcess(processModel);
  }

}
