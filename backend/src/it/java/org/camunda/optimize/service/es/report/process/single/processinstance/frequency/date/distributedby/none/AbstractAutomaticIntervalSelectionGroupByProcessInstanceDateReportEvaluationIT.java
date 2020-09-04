/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.date.distributedby.none;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

public abstract class AbstractAutomaticIntervalSelectionGroupByProcessInstanceDateReportEvaluationIT
  extends AbstractIT {

  @RegisterExtension
  @Order(4)
  protected EngineDatabaseExtension engineDatabaseExtension =
    new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  protected abstract void updateProcessInstanceDates(Map<String, OffsetDateTime> updates) throws SQLException;

  protected abstract ProcessReportDataDto getGroupByDateReportData(String key, String version);

  @Test
  public void automaticIntervalSelectionWorks() throws SQLException {
    // given
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto1.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto1.getDefinitionId());
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday);
    updates.put(processInstanceDto3.getId(), startOfToday.minusDays(1));
    updateProcessInstanceDates(updates);


    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getGroupByDateReportData(
      processInstanceDto1.getProcessDefinitionKey(),
      processInstanceDto1.getProcessDefinitionVersion()
    );
    ReportMapResultDto result = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
    assertThat(resultData.get(0).getValue(), is(1.));
    assertThat(resultData.get(resultData.size() - 1).getValue(), is(2.));
  }

  @Test
  public void automaticIntervalSelectionTakesAllProcessInstancesIntoAccount() throws SQLException {
    //given
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto1.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto1.getDefinitionId());
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday.plusDays(2));
    updates.put(processInstanceDto3.getId(), startOfToday.plusDays(5));
    updateProcessInstanceDates(updates);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getGroupByDateReportData(
      processInstanceDto1.getProcessDefinitionKey(),
      processInstanceDto1.getProcessDefinitionVersion()
    );
    ReportMapResultDto result = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
    assertThat(resultData.stream().map(MapResultEntryDto::getValue).mapToInt(Double::intValue).sum(), is(3));
    assertThat(resultData.get(0).getValue(), is(1.));
    assertThat(resultData.get(resultData.size() - 1).getValue(), is(1.));
  }

  @Test
  public void automaticIntervalSelectionForNoData() {
    // given
    ProcessDefinitionEngineDto engineDto = deploySimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getGroupByDateReportData(engineDto.getKey(), engineDto.getVersionAsString());
    ReportMapResultDto result = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(0));
  }

  @Test
  public void automaticIntervalSelectionForOneDataPoint() {
    // given there is only one data point
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getGroupByDateReportData(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportMapResultDto result = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then the single data point should be grouped by month
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(1));
    ZonedDateTime nowStrippedToMonth = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS);
    String nowStrippedToMonthAsString = localDateTimeToString(nowStrippedToMonth);
    assertThat(resultData.get(0).getKey(), is(nowStrippedToMonthAsString));
    assertThat(resultData.get(0).getValue(), is(1.));
  }

  @Test
  public void combinedReportsWithDistinctRanges() throws Exception {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto procDefFirstRange = startProcessInstancesInDayRange(now.plusDays(1), now.plusDays(3));
    ProcessDefinitionEngineDto procDefSecondRange = startProcessInstancesInDayRange(now.plusDays(4), now.plusDays(6));
    importAllEngineEntitiesFromScratch();
    String singleReportId = createNewSingleReport(procDefFirstRange);
    String singleReportId2 = createNewSingleReport(procDefSecondRange);

    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void combinedReportsWithOneIncludingRange() throws Exception {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto procDefFirstRange = startProcessInstancesInDayRange(now.plusDays(1), now.plusDays(6));
    ProcessDefinitionEngineDto procDefSecondRange = startProcessInstancesInDayRange(now.plusDays(3), now.plusDays(5));
    importAllEngineEntitiesFromScratch();
    String singleReportId = createNewSingleReport(procDefFirstRange);
    String singleReportId2 = createNewSingleReport(procDefSecondRange);

    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void combinedReportsWithIntersectingRange() throws Exception {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto procDefFirstRange = startProcessInstancesInDayRange(now.plusDays(1), now.plusDays(4));
    ProcessDefinitionEngineDto procDefSecondRange = startProcessInstancesInDayRange(now.plusDays(3), now.plusDays(6));
    String singleReportId = createNewSingleReport(procDefFirstRange);
    String singleReportId2 = createNewSingleReport(procDefSecondRange);

    importAllEngineEntitiesFromScratch();


    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void combinedReportsGroupedByStartAndEndDate() throws Exception {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    ProcessInstanceEngineDto procInstMin = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto procInstMax = engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    changeProcessInstanceDates(procInstMin, now.plusDays(1), now.plusDays(2));
    changeProcessInstanceDates(procInstMax, now.plusDays(3), now.plusDays(6));

    ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(processDefinition.getVersionAsString())
      .setDateInterval(GroupByDateUnit.AUTOMATIC)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE)
      .build();
    String singleReportId = createNewSingleReport(reportDataDto);

    ProcessReportDataDto reportDataDto2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(processDefinition.getVersionAsString())
      .setDateInterval(GroupByDateUnit.AUTOMATIC)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    String singleReportId2 = createNewSingleReport(reportDataDto2);

    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  private void changeProcessInstanceDates(final ProcessInstanceEngineDto procInstMin,
                                          final ZonedDateTime startDate,
                                          final ZonedDateTime endDate) {
    engineDatabaseExtension.changeProcessInstanceStartDate(procInstMin.getId(), startDate.toOffsetDateTime());
    engineDatabaseExtension.changeProcessInstanceEndDate(procInstMin.getId(), endDate.toOffsetDateTime());
  }

  private void assertResultIsInCorrectRanges(
    ZonedDateTime startRange,
    ZonedDateTime endRange,
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap,
    int resultSize) {
    assertThat(resultMap.size(), is(resultSize));
    for (AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> result : resultMap.values()) {
      final List<MapResultEntryDto> resultData = result.getResult().getData();
      assertThat(resultData.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
      assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startRange)));
      assertIsInRangeOfLastInterval(resultData.get(resultData.size() - 1).getKey(), startRange, endRange);
    }
  }

  private void assertIsInRangeOfLastInterval(String lastIntervalAsString,
                                             ZonedDateTime startTotal,
                                             ZonedDateTime endTotal) {
    long totalDuration = endTotal.toInstant().toEpochMilli() - startTotal.toInstant().toEpochMilli();
    long interval = totalDuration / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    assertThat(
      lastIntervalAsString,
      greaterThanOrEqualTo(localDateTimeToString(endTotal.minus(interval, ChronoUnit.MILLIS)))
    );
    assertThat(lastIntervalAsString, lessThan(localDateTimeToString(endTotal)));
  }

  private String createNewSingleReport(ProcessDefinitionEngineDto engineDto) {
    return createNewSingleReport(getGroupByDateReportData(engineDto.getKey(), engineDto.getVersionAsString()));
  }

  private String createNewSingleReport(ProcessReportDataDto reportDataDto) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportDataDto);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private ProcessDefinitionEngineDto startProcessInstancesInDayRange(ZonedDateTime min,
                                                                     ZonedDateTime max) throws SQLException {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    ProcessInstanceEngineDto procInstMin = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto procInstMax = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    updateProcessInstanceDate(min, procInstMin);
    updateProcessInstanceDate(max, procInstMax);
    return processDefinition;
  }

  protected abstract void updateProcessInstanceDate(ZonedDateTime min,
                                                    ProcessInstanceEngineDto procInstMin) throws SQLException;

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
    processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    return processInstanceEngineDto;
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleServiceTaskProcess());
  }

  private String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeExtension.getDateTimeFormatter().format(time);
  }

}
