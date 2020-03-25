/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.duration.groupby.date.distributed_by.usertask;

import com.google.common.collect.ImmutableList;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.getAggregationTypesAsList;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.mapToChronoUnit;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

public abstract class UserTaskDurationByUserTaskDateByUserTaskReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  protected static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";
  protected static final String USER_TASK_1_NAME = "userTask1Name";
  private static final String USER_TASK_2_NAME = "userTask2Name";

  private static Stream<GroupByDateUnit> staticGroupByDateUnits() {
    return Arrays.stream(GroupByDateUnit.values()).filter(g -> !g.equals(GroupByDateUnit.AUTOMATIC));
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());

    final long expectedDuration = 20L;
    changeDuration(processInstance, expectedDuration);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy()).isNotNull();
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(resultReportDataDto.getGroupBy().getValue())
      .extracting(DateGroupByValueDto.class::cast)
      .extracting(DateGroupByValueDto::getUnit)
      .isEqualTo(GroupByDateUnit.DAY);
    assertThat(resultReportDataDto.getConfiguration().getDistributedBy()).isEqualTo(DistributedBy.USER_TASK);

    final ReportHyperMapResultDto result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(localDateTimeToString(startOfToday))
        .distributedByContains(USER_TASK_1, expectedDuration, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());

    final long expectedDuration = 20L;
    changeDuration(processInstance, expectedDuration);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportData);
    final String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReportById(reportId);

    // then
    final ReportHyperMapResultDto result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(localDateTimeToString(startOfToday))
        .distributedByContains(USER_TASK_1, expectedDuration, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final OffsetDateTime today = OffsetDateTime.now();

    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());
    changeUserTaskDate(processInstance1, USER_TASK_1, today);
    changeUserTaskDate(processInstance1, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 10L);
    changeDuration(processInstance1, USER_TASK_2, 10L);

    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance2.getId());
    changeUserTaskDate(processInstance2, USER_TASK_1, today);
    changeUserTaskDate(processInstance2, USER_TASK_2, today.minusDays(1));
    changeDuration(processInstance2, USER_TASK_1, 20L);
    changeDuration(processInstance2, USER_TASK_2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final Map<AggregationType, ReportHyperMapResultDto> results = evaluateHyperMapReportForAllAggTypes(reportData);

    // then
    getAggregationTypesAsList().forEach((AggregationType aggType) -> {
      ReportHyperMapResultDto result = results.get(aggType);
      assertThat(result.getData()).isNotNull();

      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .groupByContains(groupedByDayDateAsString(today))
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
          .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurations(10L, 20L).get(aggType), USER_TASK_1_NAME)
        .groupByContains(groupedByDayDateAsString(today.minusDays(1)))
          .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurations(10L, 20L).get(aggType), USER_TASK_2_NAME)
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .doAssert(result);
      // @formatter:on
    });
  }

  @Test
  public void resultIsSortedInDescendingOrder() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 10L);
    changeDuration(processInstance1, USER_TASK_2, 10L);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));
    changeDuration(processInstance2, USER_TASK_1, 10L);
    changeDuration(processInstance2, USER_TASK_2, 10L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
        .distributedByContains(USER_TASK_2, 10L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(4)))
        .distributedByContains(USER_TASK_2, 10L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 10L);
    changeDuration(processInstance1, USER_TASK_2, 10L);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));
    changeDuration(processInstance2, USER_TASK_1, 10L);
    changeDuration(processInstance2, USER_TASK_2, 10L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, 10L, USER_TASK_2_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
        .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
        .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(4)))
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, 10L, USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 20L);
    changeDuration(processInstance1, USER_TASK_2, 10L);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(2));
    changeDuration(processInstance2, USER_TASK_1, 10L);
    changeDuration(processInstance2, USER_TASK_2, 20L);

    ProcessInstanceEngineDto processInstance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance3, USER_TASK_1, referenceDate.minusDays(3));
    changeUserTaskDate(processInstance3, USER_TASK_2, referenceDate.minusDays(3));
    changeDuration(processInstance3, USER_TASK_1, 30L);
    changeDuration(processInstance3, USER_TASK_2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
        .distributedByContains(USER_TASK_1, 20L, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, 10L, USER_TASK_2_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
        .distributedByContains(USER_TASK_2, 20L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
        .distributedByContains(USER_TASK_1, 30L, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, 20L, USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleBuckets_noFilter_resultLimitedByConfig() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 10L);
    changeDuration(processInstance1, USER_TASK_2, 10L);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));
    changeDuration(processInstance2, USER_TASK_1, 10L);
    changeDuration(processInstance2, USER_TASK_2, 10L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .isComplete(false)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
        .distributedByContains(USER_TASK_2, 10L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void userTasksStartedAtSameIntervalAreGroupedTogether() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(2));
    changeDuration(processInstance1, USER_TASK_1, 10L);
    changeDuration(processInstance1, USER_TASK_2, 10L);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(1));
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(2));
    changeDuration(processInstance2, USER_TASK_1, 20L);
    changeDuration(processInstance2, USER_TASK_2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 15L, USER_TASK_1_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
        .distributedByContains(USER_TASK_2, 15L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void emptyIntervalBetweenTwoUserTaskDates() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(3));
    changeDuration(processInstance1, USER_TASK_1, 10L);
    changeDuration(processInstance1, USER_TASK_2, 10L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
        .distributedByContains(USER_TASK_2, 10L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("staticGroupByDateUnits")
  public void countGroupByDateUnit(final GroupByDateUnit groupByDateUnit) {
    // given
    final ChronoUnit groupByUnitAsChrono = mapToChronoUnit(groupByDateUnit);
    final int groupingCount = 5;
    OffsetDateTime referenceDate = OffsetDateTime.now();

    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    List<ProcessInstanceEngineDto> processInstanceDtos = IntStream.range(0, groupingCount)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto =
          engineIntegrationExtension.startProcessInstance(processDefinition.getId());
        engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
    updateUserTaskTime(processInstanceDtos, referenceDate, groupByUnitAsChrono);
    processInstanceDtos.forEach(procInst -> changeDuration(procInst, 10L));

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, groupByDateUnit);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    HyperMapAsserter.GroupByAdder groupByAdder = HyperMapAsserter.asserter()
      .processInstanceCount(groupingCount)
      .groupByContains(groupedByDateAsString(referenceDate.minus(0, groupByUnitAsChrono), groupByUnitAsChrono))
      .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME);

    for (int i = 1; i < groupingCount; i++) {
      groupByAdder = groupByAdder
        .groupByContains(groupedByDateAsString(referenceDate.minus(i, groupByUnitAsChrono), groupByUnitAsChrono))
        .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME);
    }
    groupByAdder.doAssert(result);
  }

  @Test
  public void otherProcessDefinitionsDoNotAffectResult() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 10L);

    ProcessDefinitionEngineDto processDefinition2 = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(1));
    changeDuration(processInstance2, USER_TASK_1, 50L);


    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition1);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
      .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantUserTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReportData(processKey, "1", GroupByDateUnit.DAY);
    reportData.setTenantIds(selectedTenants);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void filterWorks() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance1, USER_TASK_1, 10L);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto> processFilterDtoList = ProcessFilterBuilder.filter()
      .completedInstancesOnly().add().buildList();
    reportData.setFilter(processFilterDtoList);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(groupedByDayDateAsString(referenceDate))
      .distributedByContains(USER_TASK_1, 10L, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void automaticIntervalSelection_simpleSetup() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday);
    updates.put(processInstanceDto3.getId(), startOfToday.minusDays(1));
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDates(updates);
    changeDuration(processInstanceDto1, 10L);
    changeDuration(processInstanceDto2, 10L);
    changeDuration(processInstanceDto3, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.AUTOMATIC);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    final List<HyperMapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertFirstValueEquals(resultData, 10L);
    assertLastValueEquals(resultData, 20L);
  }

  @Test
  public void automaticIntervalSelection_takesAllUserTasksIntoAccount() {
    //given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday.plusDays(2));
    updates.put(processInstanceDto3.getId(), startOfToday.plusDays(5));
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDates(updates);
    changeDuration(processInstanceDto1, 10L);
    changeDuration(processInstanceDto2, 20L);
    changeDuration(processInstanceDto3, 50L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.AUTOMATIC);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    final List<HyperMapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertFirstValueEquals(resultData, 50L);
    assertLastValueEquals(resultData, 10L);
    final int sumOfAllValues = resultData.stream()
      .map(HyperMapResultEntryDto::getValue)
      .flatMap(List::stream)
      .filter(Objects::nonNull)
      .map(MapResultEntryDto::getValue)
      .filter(Objects::nonNull)
      .mapToInt(Long::intValue).sum();
    assertThat(sumOfAllValues).isEqualTo(80);
  }

  @Test
  public void automaticIntervalSelection_forNoData() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.AUTOMATIC);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    final List<HyperMapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isEmpty();
  }

  @Test
  public void automaticIntervalSelection_forOneDataPoint() {
    // given there is only one data point
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.AUTOMATIC);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then the single data point should be grouped by month
    final List<HyperMapResultEntryDto> resultData = result.getData();
    ZonedDateTime nowStrippedToMonth = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS);
    String nowStrippedToMonthAsString = localDateTimeToString(nowStrippedToMonth);
    assertThat(resultData).hasSize(1);
    assertThat(resultData).first().extracting(HyperMapResultEntryDto::getKey).isEqualTo(nowStrippedToMonthAsString);
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance1, 10L);

    ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersion(ALL_VERSIONS);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
        .distributedByContains(USER_TASK_2, 20L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 15L, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    ProcessDefinitionEngineDto firstDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance1, 10L);

    ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersions(Arrays.asList(
      firstDefinition.getVersionAsString(),
      latestDefinition.getVersionAsString()
    ));
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
        .distributedByContains(USER_TASK_2, 20L, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, 15L, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance1, 10L);

    ProcessDefinitionEngineDto latestDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersion(ALL_VERSIONS);
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
        .distributedByContains(USER_TASK_1, 15L, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance1, 10L);

    ProcessDefinitionEngineDto latestDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance2, 20L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersions(Arrays.asList(
      firstDefinition.getVersionAsString(),
      latestDefinition.getVersionAsString()
    ));
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
      .distributedByContains(USER_TASK_1, 15L, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  private void assertLastValueEquals(final List<HyperMapResultEntryDto> resultData, final long expected) {
    assertThat(resultData).last().extracting(HyperMapResultEntryDto::getValue)
      .extracting(e -> e.get(0))
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(expected);
  }

  private void assertFirstValueEquals(final List<HyperMapResultEntryDto> resultData, final long expected) {
    assertThat(resultData).first().extracting(HyperMapResultEntryDto::getValue)
      .extracting(e -> e.get(0))
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(expected);
  }

  private void updateUserTaskTime(List<ProcessInstanceEngineDto> procInsts,
                                  OffsetDateTime now,
                                  ChronoUnit unit) {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach(i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.minus(i, unit);
        idToNewStartDate.put(id, newStartDate);
      });
    changeUserTaskDates(idToNewStartDate);
  }

  protected ProcessReportDataDto createReportData(final String processDefinitionKey, final String version,
                                                  final GroupByDateUnit groupByDateUnit) {
    return createReportData(processDefinitionKey, ImmutableList.of(version), groupByDateUnit);
  }

  protected ProcessReportDataDto createReportData(final String processDefinitionKey, final List<String> versions,
                                                  final GroupByDateUnit groupByDateUnit) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(getUserTaskDurationTime())
      .setReportDataType(getReportDataType())
      .setDateInterval(groupByDateUnit)
      .build();
  }

  private ProcessReportDataDto createGroupedByDayReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReportData(processDefinition, GroupByDateUnit.DAY);
  }

  protected ProcessReportDataDto createReportData(final ProcessDefinitionEngineDto processDefinition,
                                                  final GroupByDateUnit groupByDateUnit) {
    return createReportData(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion()),
      groupByDateUnit
    );
  }

  private Map<AggregationType, ReportHyperMapResultDto> evaluateHyperMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, ReportHyperMapResultDto> resultsMap = new HashMap<>();
    getAggregationTypesAsList().forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();
      resultsMap.put(aggType, result);
    });
    return resultsMap;
  }

  private String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final ProcessDefinitionEngineDto processDefinitionEngineDto = deployOneUserTaskDefinition(processKey, tenant);
        engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
      });

    return processKey;
  }

  protected ProcessDefinitionEngineDto deployOneUserTaskDefinition() {
    return deployOneUserTaskDefinition("aProcess", null);
  }

  private ProcessDefinitionEngineDto deployOneUserTaskDefinition(String key, String tenantId) {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
        .name(USER_TASK_1_NAME)
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
        .name(USER_TASK_1_NAME)
      .userTask(USER_TASK_2)
        .name(USER_TASK_2_NAME)
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  @SuppressWarnings("SameParameterValue")
  protected void changeUserTaskStartDate(final ProcessInstanceEngineDto processInstanceDto,
                                         final OffsetDateTime now,
                                         final String userTaskId,
                                         final long offsetDuration) {
    engineDatabaseExtension.changeUserTaskStartDate(
      processInstanceDto.getId(),
      userTaskId,
      now.minus(offsetDuration, ChronoUnit.MILLIS)
    );
  }

  @SuppressWarnings("SameParameterValue")
  protected void changeUserTaskClaimDate(final ProcessInstanceEngineDto processInstanceDto,
                                         final OffsetDateTime now,
                                         final String userTaskKey,
                                         final long offsetDuration) {

    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto ->
        {
          try {
            engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              now.minus(offsetDuration, ChronoUnit.MILLIS)
            );
          } catch (SQLException e) {
            throw new OptimizeIntegrationTestException(e);
          }
        }
      );
  }

  private String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeExtension.getDateTimeFormatter().format(time);
  }

  protected String groupedByDayDateAsString(final OffsetDateTime referenceDate) {
    return groupedByDateAsString(referenceDate, ChronoUnit.DAYS);
  }

  private String groupedByDateAsString(final OffsetDateTime referenceDate, final ChronoUnit chronoUnit) {
    return localDateTimeToString(truncateToStartOfUnit(referenceDate, chronoUnit));
  }

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final long duration);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration);

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  protected abstract ProcessGroupByType getGroupByType();

  protected abstract ProcessReportDataType getReportDataType();

  protected abstract void changeUserTaskDates(final Map<String, OffsetDateTime> updates);

  protected abstract void changeUserTaskDate(final ProcessInstanceEngineDto processInstance,
                                             final String userTaskKey,
                                             final OffsetDateTime dateToChangeTo);
}
