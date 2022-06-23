/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.groupby.date.distributedby.none;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.service.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public abstract class AbstractAutomaticIntervalSelectionGroupByProcessInstanceDateReportEvaluationIT
  extends AbstractIT {

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
    final List<MapResultEntryDto> resultData = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(resultData.get(0).getValue()).isEqualTo(1.);
    assertThat(resultData.get(resultData.size() - 1).getValue()).isEqualTo(2.);
  }

  @Test
  public void automaticIntervalSelectionTakesAllProcessInstancesIntoAccount() throws SQLException {
    // given
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
    final List<MapResultEntryDto> resultData = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(resultData.stream().map(MapResultEntryDto::getValue).mapToInt(Double::intValue).sum()).isEqualTo(3);
    assertThat(resultData.get(0).getValue()).isEqualTo(1.);
    assertThat(resultData.get(resultData.size() - 1).getValue()).isEqualTo(1.);
  }

  @Test
  public void automaticIntervalSelectionForNoData() {
    // given
    ProcessDefinitionEngineDto engineDto = deploySimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getGroupByDateReportData(engineDto.getKey(), engineDto.getVersionAsString());
    final List<MapResultEntryDto> resultData = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then
    assertThat(resultData).isEmpty();
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
    final List<MapResultEntryDto> resultData = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then the single data point should be grouped by month
    assertThat(resultData).hasSize(1);
    ZonedDateTime nowStrippedToMonth = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS);
    String nowStrippedToMonthAsString = localDateTimeToString(nowStrippedToMonth);
    assertThat(resultData.get(0).getKey()).isEqualTo(nowStrippedToMonthAsString);
    assertThat(resultData.get(0).getValue()).isEqualTo(1.);
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
    CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap = result.getData();
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
    CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap = result.getData();
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
    CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void combinedReportsGroupedByStartAndEndDate() {
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
      .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_END_DATE)
      .build();
    String singleReportId = createNewSingleReport(reportDataDto);

    ProcessReportDataDto reportDataDto2 = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(processDefinition.getVersionAsString())
      .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    String singleReportId2 = createNewSingleReport(reportDataDto2);

    importAllEngineEntitiesFromScratch();

    // when
    CombinedProcessReportResultDataDto<List<MapResultEntryDto>> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap = result.getData();
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
    Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap,
    int resultSize) {
    assertThat(resultMap).hasSize(resultSize);
    for (AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> result : resultMap.values()) {
      final List<MapResultEntryDto> resultData = result.getResult().getFirstMeasureData();
      assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
      assertThat(resultData.get(0).getKey()).isEqualTo(localDateTimeToString(startRange));
      assertIsInRangeOfLastInterval(resultData.get(resultData.size() - 1).getKey(), startRange, endRange);
    }
  }

  private void assertIsInRangeOfLastInterval(String lastIntervalAsString,
                                             ZonedDateTime startTotal,
                                             ZonedDateTime endTotal) {
    long totalDuration = endTotal.toInstant().toEpochMilli() - startTotal.toInstant().toEpochMilli();
    long interval = totalDuration / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    assertThat(lastIntervalAsString)
      .isGreaterThanOrEqualTo(localDateTimeToString(endTotal.minus(interval, ChronoUnit.MILLIS)));
    assertThat(lastIntervalAsString).isLessThan(localDateTimeToString(endTotal));
  }

  private String createNewSingleReport(ProcessDefinitionEngineDto engineDto) {
    return createNewSingleReport(getGroupByDateReportData(engineDto.getKey(), engineDto.getVersionAsString()));
  }

  private String createNewSingleReport(ProcessReportDataDto reportDataDto) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportDataDto);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleReport(SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
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
