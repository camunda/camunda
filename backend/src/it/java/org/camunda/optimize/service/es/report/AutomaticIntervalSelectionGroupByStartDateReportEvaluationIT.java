/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessCountReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByStartDate;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;


public class AutomaticIntervalSelectionGroupByStartDateReportEvaluationIT {

  private EngineIntegrationRule engineRule = new EngineIntegrationRule();
  private ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  private EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();


  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void automaticIntervalSelectionWorks() throws SQLException {
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto1.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto1.getDefinitionId());
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday);
    updates.put(processInstanceDto3.getId(), startOfToday.minusDays(1));
    engineDatabaseRule.updateProcessInstanceStartDates(updates);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createCountProcessInstanceFrequencyGroupByStartDate(
        processInstanceDto1.getProcessDefinitionKey(),
        processInstanceDto1.getProcessDefinitionVersion(),
        GroupByDateUnit.AUTOMATIC
      );
    ProcessCountReportMapResultDto result = evaluateReportAndReturnResult(reportData);

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
    assertThat(resultData.get(0).getValue(), is(2L));
    assertThat(resultData.get(resultData.size() - 1).getValue(), is(1L));
  }

  @Test
  public void automaticIntervalSelectionTakesAllProcessInstancesIntoAccount() throws SQLException {
    ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(processInstanceDto1.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto1.getDefinitionId());
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday.plusDays(2));
    updates.put(processInstanceDto3.getId(), startOfToday.plusDays(5));
    engineDatabaseRule.updateProcessInstanceStartDates(updates);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createCountProcessInstanceFrequencyGroupByStartDate(
        processInstanceDto1.getProcessDefinitionKey(),
        processInstanceDto1.getProcessDefinitionVersion(),
        GroupByDateUnit.AUTOMATIC
      );
    ProcessCountReportMapResultDto result = evaluateReportAndReturnResult(reportData);

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
    assertThat(resultData.stream().map(MapResultEntryDto::getValue).mapToInt(Long::intValue).sum(), is(3));
    assertThat(resultData.get(0).getValue(), is(1L));
    assertThat(resultData.get(resultData.size() - 1).getValue(), is(1L));
  }

  @Test
  public void automaticIntervalSelectionForNoData() {
    ProcessDefinitionEngineDto engineDto = deploySimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createCountProcessInstanceFrequencyGroupByStartDate(
        engineDto.getKey(),
        engineDto.getVersionAsString(),
        GroupByDateUnit.AUTOMATIC
      );
    ProcessCountReportMapResultDto result = evaluateReportAndReturnResult(reportData);

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(0));
  }

  @Test
  public void automaticIntervalSelectionForOneDataPoint() {
    // given there is only one data point
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createCountProcessInstanceFrequencyGroupByStartDate(
        engineDto.getProcessDefinitionKey(),
        engineDto.getProcessDefinitionVersion(),
        GroupByDateUnit.AUTOMATIC
      );
    ProcessCountReportMapResultDto result = evaluateReportAndReturnResult(reportData);

    // then the single data point should be grouped by month
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(1));
    ZonedDateTime nowStrippedToMonth = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS);
    String nowStrippedToMonthAsString = localDateTimeToString(nowStrippedToMonth);
    assertThat(resultData.get(0).getKey(), is(nowStrippedToMonthAsString));
    assertThat(resultData.get(0).getValue(), is(1L));
  }

  @Test
  public void combinedReportsWithDistinctRanges() throws Exception {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto procDefFirstRange = startProcessInstancesInDayRange(now.plusDays(1), now.plusDays(3));
    ProcessDefinitionEngineDto procDefSecondRange = startProcessInstancesInDayRange(now.plusDays(4), now.plusDays(6));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    String singleReportId = createNewSingleReport(procDefFirstRange);
    String singleReportId2 = createNewSingleReport(procDefSecondRange);

    // when
    CombinedProcessReportResultDataDto<ProcessCountReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void combinedReportsWithOneIncludingRange() throws Exception {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto procDefFirstRange = startProcessInstancesInDayRange(now.plusDays(1), now.plusDays(6));
    ProcessDefinitionEngineDto procDefSecondRange = startProcessInstancesInDayRange(now.plusDays(3), now.plusDays(5));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    String singleReportId = createNewSingleReport(procDefFirstRange);
    String singleReportId2 = createNewSingleReport(procDefSecondRange);

    // when
    CombinedProcessReportResultDataDto<ProcessCountReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  @Test
  public void combinedReportsWithIntersectingRange() throws Exception {
    // given
    ZonedDateTime now = ZonedDateTime.now();
    ProcessDefinitionEngineDto procDefFirstRange = startProcessInstancesInDayRange(now.plusDays(1), now.plusDays(4));
    ProcessDefinitionEngineDto procDefSecondRange = startProcessInstancesInDayRange(now.plusDays(3), now.plusDays(6));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    String singleReportId = createNewSingleReport(procDefFirstRange);
    String singleReportId2 = createNewSingleReport(procDefSecondRange);

    // when
    CombinedProcessReportResultDataDto<ProcessCountReportMapResultDto> result =
      evaluateUnsavedCombined(createCombinedReport(singleReportId, singleReportId2));

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(now.plusDays(1), now.plusDays(6), resultMap, 2);
  }

  private void assertResultIsInCorrectRanges(ZonedDateTime startRange,
                                             ZonedDateTime endRange,
                                             Map<String, ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto>> resultMap,
                                             int resultSize) {
    assertThat(resultMap.size(), is(resultSize));
    for (ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> result : resultMap.values()) {
      final List<MapResultEntryDto<Long>> resultData = result.getResult().getData();
      assertThat(resultData.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
      assertThat(resultData.get(resultData.size() - 1).getKey(), is(localDateTimeToString(startRange)));
      assertIsInRangeOfLastInterval(resultData.get(0).getKey(), startRange, endRange);
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
    String singleReportId = createNewSingleReport();
    ProcessReportDataDto reportDataDto =
      createCountProcessInstanceFrequencyGroupByStartDate(
        engineDto.getKey(),
        engineDto.getVersionAsString(),
        GroupByDateUnit.AUTOMATIC
      );
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(reportDataDto);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewSingleReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private ProcessDefinitionEngineDto startProcessInstancesInDayRange(ZonedDateTime min,
                                                                     ZonedDateTime max) throws SQLException {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    ProcessInstanceEngineDto procInstMin = engineRule.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto procInstMax = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeProcessInstanceStartDate(procInstMin.getId(), min.toOffsetDateTime());
    engineDatabaseRule.changeProcessInstanceStartDate(procInstMax.getId(), max.toOffsetDateTime());
    return processDefinition;
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
    processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    return processInstanceEngineDto;
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
  }

  private <T extends ProcessReportResultDto> CombinedProcessReportResultDataDto<T> evaluateUnsavedCombined(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      // @formatter:off
      .execute(new TypeReference<CombinedReportEvaluationResultDto<T>>() {})
      // @formatter:on
      .getResult();
  }

  private ProcessCountReportMapResultDto evaluateReportAndReturnResult(final ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto>>() {})
      // @formatter:on
      .getResult();
  }

  private void updateReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeRule.getDateTimeFormatter().format(time);
  }

}
