/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.frequency;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessCountReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByStartDate;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;


public class CountProcessInstanceFrequencyByStartDateReportEvaluationIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();


  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void simpleReportEvaluation() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    StartDateGroupByDto startDateGroupByDto = (StartDateGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(startDateGroupByDto.getValue().getUnit(), is(GroupByDateUnit.DAY));

    final ProcessCountReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getProcessInstanceCount(), is(1L));
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(1L));
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    String reportId = createAndStoreDefaultReportDefinition(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );

    // when
    ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluationResponse = evaluateReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    StartDateGroupByDto startDateGroupByDto = (StartDateGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(startDateGroupByDto.getValue().getUnit(), is(GroupByDateUnit.DAY));

    final ProcessCountReportMapResultDto result = evaluationResponse.getResult();
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(1L));
  }

  @Test
  public void resultIsSortedInDescendingOrder() throws Exception {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), OffsetDateTime.now().minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    final ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect ascending order
      contains(resultKeys.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  @Test
  public void testEmptyBucketsAreReturnedForStartDateFilterPeriod() throws Exception {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate.minusDays(2));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );

    final RelativeDateFilterDataDto dateFilterDataDto = new RelativeDateFilterDataDto();
    dateFilterDataDto.setStart(new RelativeDateFilterStartDto(
      4L,
      RelativeDateFilterUnit.DAYS
    ));
    reportData.setFilter(Collections.singletonList(new StartDateFilterDto(dateFilterDataDto)));

    final ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(5));

    assertThat(
      resultData.get(0).getKey(),
      is(embeddedOptimizeRule.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS))
    );
    assertThat(resultData.get(0).getValue(), is(1L));

    assertThat(
      resultData.get(1).getKey(),
      is(embeddedOptimizeRule.formatToHistogramBucketKey(startDate.minusDays(1), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(1).getValue(), is(0L));

    assertThat(
      resultData.get(2).getKey(),
      is(embeddedOptimizeRule.formatToHistogramBucketKey(startDate.minusDays(2), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(2).getValue(), is(1L));

    assertThat(
      resultData.get(3).getKey(),
      is(embeddedOptimizeRule.formatToHistogramBucketKey(startDate.minusDays(3), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(3).getValue(), is(0L));

    assertThat(
      resultData.get(4).getKey(),
      is(embeddedOptimizeRule.formatToHistogramBucketKey(startDate.minusDays(4), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(4).getValue(), is(0L));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() throws SQLException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), OffsetDateTime.now().minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect ascending order
      contains(resultKeys.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() throws SQLException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), OffsetDateTime.now().minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto4 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto4.getId(), OffsetDateTime.now().minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto5 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto5.getId(), OffsetDateTime.now().minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto6 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto6.getId(), OffsetDateTime.now().minusDays(2));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getIsComplete(), is(true));
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    final List<Long> resultValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(
      resultValues,
      contains(resultValues.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  @Test
  public void multipleBuckets_noFilter_resultLimitedByConfig() throws SQLException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), OffsetDateTime.now().minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto4 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto4.getId(), OffsetDateTime.now().minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto5 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto5.getId(), OffsetDateTime.now().minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto6 = engineRule.startProcessInstance(definitionId);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto6.getId(), OffsetDateTime.now().minusDays(2));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    final ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    assertThat(result.getIsComplete(), is(false));
  }

  @Test
  public void processInstancesStartedAtSameIntervalAreGroupedTogether() throws Exception {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);

    final String expectedStringToday = localDateTimeToString(startOfToday);
    final Optional<MapResultEntryDto<Long>> todayEntry = resultData.stream()
      .filter(e -> expectedStringToday.equals(e.getKey()))
      .findFirst();
    assertThat(todayEntry.isPresent(), is(true));
    assertThat(todayEntry.get().getValue(), is(2L));

    final String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    final Optional<MapResultEntryDto<Long>> yesterdayEntry = resultData.stream()
      .filter(e -> expectedStringYesterday.equals(e.getKey()))
      .findFirst();
    assertThat(yesterdayEntry.isPresent(), is(true));
    assertThat(yesterdayEntry.get().getValue(), is(1L));
  }

  @Test
  public void emptyIntervalBetweenTwoProcessInstances() throws Exception {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(2));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(3));

    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);

    final String expectedStringToday = localDateTimeToString(startOfToday);
    final Optional<MapResultEntryDto<Long>> todayEntry = resultData.stream()
      .filter(e -> expectedStringToday.equals(e.getKey()))
      .findFirst();
    assertThat(todayEntry.isPresent(), is(true));
    assertThat(todayEntry.get().getValue(), is(2L));

    final String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    final Optional<MapResultEntryDto<Long>> yesterdayEntry = resultData.stream()
      .filter(e -> expectedStringYesterday.equals(e.getKey()))
      .findFirst();
    assertThat(yesterdayEntry.isPresent(), is(true));
    assertThat(yesterdayEntry.get().getValue(), is(0L));

    final String expectedStringDayBeforeYesterday = localDateTimeToString(startOfToday.minusDays(2));
    final Optional<MapResultEntryDto<Long>> dayBeforYesterdayEntry = resultData.stream()
      .filter(e -> expectedStringDayBeforeYesterday.equals(e.getKey()))
      .findFirst();
    assertThat(dayBeforYesterdayEntry.isPresent(), is(true));
    assertThat(dayBeforYesterdayEntry.get().getValue(), is(1L));
  }

  @Test
  public void countGroupedByHour() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(5);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesStartTime(processInstanceDtos, now, ChronoUnit.HOURS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion(),
      GroupByDateUnit.HOUR
    );
    ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertStartDateResultMap(resultData, 5, now, ChronoUnit.HOURS);
  }

  private void assertStartDateResultMap(List<MapResultEntryDto<Long>> resultData,
                                        int size,
                                        OffsetDateTime now,
                                        ChronoUnit unit) {
    assertStartDateResultMap(resultData, size, now, unit, 1L);
  }

  private void assertStartDateResultMap(List<MapResultEntryDto<Long>> resultData,
                                        int size,
                                        OffsetDateTime now,
                                        ChronoUnit unit,
                                        long expectedValue) {
    assertThat(resultData.size(), is(size));
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        final String expectedDateString = localDateTimeToString(finalStartOfUnit.minus((i), unit));
        assertThat(resultData.get(i).getKey(), is(expectedDateString));
        assertThat(resultData.get(i).getValue(), is(expectedValue));
      });
  }

  private void updateProcessInstancesStartTime(List<ProcessInstanceEngineDto> procInsts,
                                               OffsetDateTime now,
                                               ChronoUnit unit) throws SQLException {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach(i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.minus(i, unit);
        idToNewStartDate.put(id, newStartDate);
      });
    engineDatabaseRule.updateProcessInstanceStartDates(idToNewStartDate);
  }

  @Test
  public void countGroupedByDay() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesStartTime(processInstanceDtos, now, ChronoUnit.DAYS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertStartDateResultMap(resultData, 8, now, ChronoUnit.DAYS);
  }

  @Test
  public void countGroupedByWeek() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesStartTime(processInstanceDtos, now, ChronoUnit.WEEKS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion(),
      GroupByDateUnit.WEEK
    );
    ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertStartDateResultMap(resultData, 8, now, ChronoUnit.WEEKS);
  }

  @Test
  public void countGroupedByMonth() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(3);
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updateProcessInstancesStartTime(processInstanceDtos, now, ChronoUnit.MONTHS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion(),
      GroupByDateUnit.MONTH
    );
    ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertStartDateResultMap(resultData, 3, now, ChronoUnit.MONTHS);
  }

  @Test
  public void countGroupedByYear() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesStartTime(processInstanceDtos, now, ChronoUnit.YEARS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion(),
      GroupByDateUnit.YEAR
    );
    ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertStartDateResultMap(resultData, 8, now, ChronoUnit.YEARS);
  }

  @Test
  public void reportAcrossAllVersions() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(), ReportConstants.ALL_VERSIONS, GroupByDateUnit.DAY
    );
    ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertStartDateResultMap(resultData, 1, OffsetDateTime.now(), ChronoUnit.DAYS, 2L);
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      GroupByDateUnit.DAY
    );
    ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertStartDateResultMap(resultData, 1, OffsetDateTime.now(), ChronoUnit.DAYS);
  }

  @Test
  public void flowNodeFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), GroupByDateUnit.DAY
    );

    List<ProcessFilterDto> flowNodeFilter = ProcessFilterBuilder.filter().executedFlowNodes()
      .id("task1")
      .add()
      .buildList();

    reportData.getFilter().addAll(flowNodeFilter);
    ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto =
      createCountProcessInstanceFrequencyGroupByStartDate("123", "1", GroupByDateUnit.DAY);
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void optimizeExceptionOnGroupByUnitIsNull() {
    // given
    ProcessReportDataDto dataDto = createCountProcessInstanceFrequencyGroupByStartDate("123", "1", GroupByDateUnit.DAY);
    StartDateGroupByDto groupByDto = (StartDateGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setUnit(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleProcesses(1).get(0);
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(int number) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    return IntStream.range(0, number)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
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

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent("startEvent")
      .exclusiveGateway("splittingGateway")
      .name("Should we go to task 1?")
      .condition("yes", "${goToTask1}")
      .serviceTask("task1")
      .camundaExpression("${true}")
      .exclusiveGateway("mergeGateway")
      .endEvent("endEvent")
      .moveToNode("splittingGateway")
      .condition("no", "${!goToTask1}")
      .serviceTask("task2")
      .camundaExpression("${true}")
      .connectTo("mergeGateway")
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }


  private String createAndStoreDefaultReportDefinition(String processDefinitionKey, String processDefinitionVersion) {
    String id = createNewReport();
    ProcessReportDataDto reportData = createCountProcessInstanceFrequencyGroupByStartDate(
      processDefinitionKey, processDefinitionVersion, GroupByDateUnit.DAY
    );
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId(id);
    report.setLastModifier("something");
    report.setName("something");
    report.setCreated(OffsetDateTime.now());
    report.setLastModified(OffsetDateTime.now());
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  private void updateReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String createNewReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluateReportById(String reportId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto>>() {});
      // @formatter:on
  }

  private ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluateReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto>>() {});
      // @formatter:on
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  private String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeRule.getDateTimeFormatter().format(time);
  }
}
