/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.frequency.date;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

public abstract class AbstractCountProcessInstanceFrequencyByDateReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  protected abstract ProcessReportDataType getTestReportDataType();

  protected abstract ProcessGroupByType getGroupByType();

  @Test
  public void simpleReportEvaluation() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();

    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(getGroupByType()));
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy().getValue()).getUnit(), is(GroupByDateUnit.DAY));

    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount(), is(1L));
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
    String reportId = createAndStoreDefaultReportDefinition(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );

    // when
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = evaluateMapReportById(
      reportId
    );

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(getGroupByType()));
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy().getValue()).getUnit(), is(GroupByDateUnit.DAY));

    final ReportMapResultDto result = evaluationResponse.getResult();
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(1L));
  }


  protected ProcessDefinitionEngineDto deployTwoRunningAndOneCompletedUserTaskProcesses(final OffsetDateTime now) throws
                                                                                                                  SQLException {
    final ProcessDefinitionEngineDto processDefinition = deploySimpleOneUserTasksDefinition();

    final ProcessInstanceEngineDto processInstance1 = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineIntegrationExtensionRule.finishAllRunningUserTasks(processInstance1.getId());
    final ProcessInstanceEngineDto processInstance2 = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstance2.getId(), now.minusDays(1));
    final ProcessInstanceEngineDto processInstance3 = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstance3.getId(), now.minusDays(2));
    return processDefinition;
  }

  @Test
  public void resultIsSortedInDescendingOrder() throws Exception {
    // given

    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto2.getId(), referenceDate.minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto3.getId(), referenceDate.minusDays(1));

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

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

  protected abstract void changeProcessInstanceDate(String processInstanceId,
                                                    final OffsetDateTime newDate) throws
                                                                                  SQLException;

  @Test
  public void testCustomOrderOnResultKeyIsApplied() throws SQLException {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    changeProcessInstanceDate(processInstanceDto.getId(), referenceDate);
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto2.getId(), referenceDate.minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto3.getId(), referenceDate.minusDays(1));

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

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
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    changeProcessInstanceDate(processInstanceDto.getId(), referenceDate);
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto2.getId(), referenceDate.minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto3.getId(), referenceDate.minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto4.getId(), referenceDate.minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto5.getId(), referenceDate.minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto6.getId(), referenceDate.minusDays(2));

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

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
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    changeProcessInstanceDate(processInstanceDto.getId(), referenceDate);
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto2.getId(), referenceDate.minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto3.getId(), referenceDate.minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto4.getId(), referenceDate.minusDays(1));
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto5.getId(), referenceDate.minusDays(2));
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtensionRule.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto6.getId(), referenceDate.minusDays(2));

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    embeddedOptimizeExtensionRule.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    assertThat(result.getIsComplete(), is(false));
  }

  @Test
  public void processInstancesStartedAtSameIntervalAreGroupedTogether() throws Exception {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();

    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    changeProcessInstanceDate(processInstanceDto.getId(), referenceDate);

    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    changeProcessInstanceDate(processInstanceDto3.getId(), referenceDate.minusDays(1));

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);

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
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    changeProcessInstanceDate(processInstanceDto.getId(), referenceDate);

    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    changeProcessInstanceDate(processInstanceDto3.getId(), referenceDate.minusDays(2));

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(3));

    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);

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
    updateProcessInstancesTime(processInstanceDtos, now, ChronoUnit.HOURS);


    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.HOUR)
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

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

  private void updateProcessInstancesTime(List<ProcessInstanceEngineDto> procInsts,
                                          OffsetDateTime now,
                                          ChronoUnit unit) throws SQLException {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach(i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.minus(i, unit);
        idToNewStartDate.put(id, newStartDate);
      });
    updateProcessInstanceDates(idToNewStartDate);
  }

  protected abstract void updateProcessInstanceDates(Map<String, OffsetDateTime> newIdToDates) throws SQLException;

  @Test
  public void countGroupedByDay() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesTime(processInstanceDtos, now, ChronoUnit.DAYS);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

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
    updateProcessInstancesTime(processInstanceDtos, now, ChronoUnit.WEEKS);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.WEEK)
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

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
    updateProcessInstancesTime(processInstanceDtos, now, ChronoUnit.MONTHS);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.MONTH)
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

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
    updateProcessInstancesTime(processInstanceDtos, now, ChronoUnit.YEARS);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.YEAR)
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertStartDateResultMap(resultData, 8, now, ChronoUnit.YEARS);
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertStartDateResultMap(resultData, 1, OffsetDateTime.now(), ChronoUnit.DAYS);
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(getTestReportDataType())
      .build();

    reportData.setTenantIds(selectedTenants);
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is((long) selectedTenants.size()));
  }

  @Test
  public void flowNodeFilterInReport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("goToTask1", false);
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()))
      .setReportDataType(getTestReportDataType())
      .build();

    List<ProcessFilterDto> flowNodeFilter = ProcessFilterBuilder.filter().executedFlowNodes()
      .id("task1")
      .add()
      .buildList();

    reportData.getFilter().addAll(flowNodeFilter);
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto =
      createGroupByStartDateReport("123", "1", GroupByDateUnit.DAY);
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void optimizeExceptionOnGroupByUnitIsNull() {
    // given
    ProcessReportDataDto dataDto = createGroupByStartDateReport("123", "1", GroupByDateUnit.DAY);
    StartDateGroupByDto groupByDto = (StartDateGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setUnit(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(int number) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    return IntStream.range(0, number)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
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
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(processModel);
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey, String processDefinitionVersion) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(getTestReportDataType())
      .build();
    return createNewReport(reportData);
  }

  private ProcessReportDataDto createGroupByStartDateReport(String processDefinitionKey,
                                                            String processDefinitionVersion,
                                                            GroupByDateUnit groupByDateUnit) {
    return ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setDateInterval(groupByDateUnit)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
  }

  protected String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeExtensionRule.getDateTimeFormatter().format(time);
  }
}