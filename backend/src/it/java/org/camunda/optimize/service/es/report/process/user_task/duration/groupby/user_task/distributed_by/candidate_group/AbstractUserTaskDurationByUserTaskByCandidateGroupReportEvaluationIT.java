/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.duration.groupby.user_task.distributed_by.candidate_group;

import com.google.common.collect.ImmutableList;
import lombok.Data;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

public abstract class AbstractUserTaskDurationByUserTaskByCandidateGroupReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";
  protected static final String USER_TASK_1 = "userTask1";
  protected static final String USER_TASK_2 = "userTask2";
  protected static final String USER_TASK_A = "userTaskA";
  protected static final String USER_TASK_B = "userTaskB";
  protected static final String FIRST_CANDIDATE_GROUP = "firstGroup";
  protected static final String SECOND_CANDIDATE_GROUP = "secondGroup";
  private static final Long UNASSIGNED_TASK_DURATION = 500L;
  protected static final Long[] SET_DURATIONS = new Long[]{10L, 20L};
  private final List<AggregationType> aggregationTypes = Arrays.asList(AggregationType.values());

  private static Stream<FlowNodeExecutionState> executionStates() {
    return Stream.of(FlowNodeExecutionState.RUNNING, FlowNodeExecutionState.COMPLETED, FlowNodeExecutionState.ALL);
  }

  @BeforeEach
  public void init() {
    // create second user
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP);
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto);

    final long setDuration = 20L;
    changeDuration(processInstanceDto, setDuration);
    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processDefinition.getVersionAsString()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.USER_TASK));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTime(), is(getUserTaskDurationTime()));
    assertThat(resultReportDataDto.getConfiguration().getDistributedBy(), is(DistributedBy.CANDIDATE_GROUP));

    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 20L)
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 20L)
      .groupByContains(USER_TASK_A)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 20L)
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
      .groupByContains(USER_TASK_B)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 20L)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessWithUnassignedTasks() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskOneWithFirstGroupAndLeaveOneUnassigned(processInstanceDto);

    changeDuration(processInstanceDto, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto, USER_TASK_A, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_B, UNASSIGNED_TASK_DURATION);
    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processDefinition.getVersionAsString()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.USER_TASK));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTime(), is(getUserTaskDurationTime()));
    assertThat(resultReportDataDto.getConfiguration().getDistributedBy(), is(DistributedBy.CANDIDATE_GROUP));

    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    assertHyperMap_ForOneProcessWithUnassignedTasks(actualResult);
  }

  protected void assertHyperMap_ForOneProcessWithUnassignedTasks(final ReportHyperMapResultDto actualResult) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), UNASSIGNED_TASK_DURATION)
      .groupByContains(USER_TASK_A)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_B)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), UNASSIGNED_TASK_DURATION)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForMultipleCandidateGroups() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    // finish first task
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    // finish second task with
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());

    changeDuration(processInstanceDto, USER_TASK_1, 10L);
    changeDuration(processInstanceDto, USER_TASK_2, 20L);
    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportHyperMapResultDto actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 10L)
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 20L)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 20L)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcesses() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskOneWithFirstGroupAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto2, USER_TASK_A, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_B, UNASSIGNED_TASK_DURATION);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    assertHyperMap_ForSeveralProcesses(actualResult);
  }

  protected void assertHyperMap_ForSeveralProcesses(final ReportHyperMapResultDto actualResult) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
        .distributedByContains(getLocalisedUnassignedLabel(), UNASSIGNED_TASK_DURATION)
      .groupByContains(USER_TASK_A)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_B)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
        .distributedByContains(getLocalisedUnassignedLabel(), UNASSIGNED_TASK_DURATION)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessesWithAllAggregationTypes() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskOneWithFirstGroupAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto2, USER_TASK_A, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_B, UNASSIGNED_TASK_DURATION);

    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      assertHyperMap_ForSeveralProcessesWithAllAggregationTypes(results, aggType);
    });
  }

  protected void assertHyperMap_ForSeveralProcessesWithAllAggregationTypes(
    final Map<AggregationType, ReportHyperMapResultDto> actualResults,
    final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
        .distributedByContains(getLocalisedUnassignedLabel(), UNASSIGNED_TASK_DURATION)
      .groupByContains(USER_TASK_A)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_B)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
        .distributedByContains(getLocalisedUnassignedLabel(), UNASSIGNED_TASK_DURATION)
      .doAssert(actualResults.get(aggType));
    // @formatter:on
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskOneWithFirstGroupAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);

    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      assertHyperMap_ForMultipleEventsWithAllAggregationTypes(results, aggType);
    });
  }

  protected void assertHyperMap_ForMultipleEventsWithAllAggregationTypes(
    final Map<AggregationType, ReportHyperMapResultDto> results, final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
        .distributedByContains(getLocalisedUnassignedLabel(), UNASSIGNED_TASK_DURATION)
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    importAndRefresh();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .isComplete(false)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 10L)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto2, USER_TASK_2, SET_DURATIONS[1]);

    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));

    // when
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .groupByContains(USER_TASK_1)
          .distributedByContains(SECOND_CANDIDATE_GROUP, null)
          .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .groupByContains(USER_TASK_2)
          .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
          .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto2, USER_TASK_2, SET_DURATIONS[1]);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.DESC));
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .groupByContains(USER_TASK_1)
          .distributedByContains(SECOND_CANDIDATE_GROUP, null)
          .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .groupByContains(USER_TASK_2)
          .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
          .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, SET_DURATIONS[0]);
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskOneWithFirstGroupAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      assertHyperMap_CustomOrderOnResultValueIsApplied(results, aggType);
    });
  }

  protected void assertHyperMap_CustomOrderOnResultValueIsApplied(
    final Map<AggregationType, ReportHyperMapResultDto> results, final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(getLocalisedUnassignedLabel(), UNASSIGNED_TASK_DURATION)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    importAndRefresh();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP,  calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP,  null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(40L))
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(3));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    importAndRefresh();

    //when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L))
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(40L))
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    importAndRefresh();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L))
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(3));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    importAndRefresh();

    //when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurationsDefaultAggr(20L, 40L))
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    final Long[] setDurations1 = new Long[]{40L, 20L};
    final Long[] setDurations2 = new Long[]{60L, 80L};

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition1.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, setDurations1[0]);
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition1.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, setDurations1[1]);

    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto3);
    changeDuration(processInstanceDto3, setDurations2[0]);
    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    changeUserTaskStartDate(processInstanceDto4, now, USER_TASK_1, UNASSIGNED_TASK_DURATION);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ReportHyperMapResultDto actualResult1 = evaluateHyperMapReport(reportData1).getResult();
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ReportHyperMapResultDto actualResult2 = evaluateHyperMapReport(reportData2).getResult();

    // then
    assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(
      setDurations1,
      setDurations2,
      actualResult1,
      actualResult2
    );
  }

  protected void assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(final Long[] setDurations1,
                                                                            final Long[] setDurations2,
                                                                            final ReportHyperMapResultDto result1,
                                                                            final ReportHyperMapResultDto result2) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP,calculateExpectedValueGivenDurationsDefaultAggr(setDurations1))
      .doAssert(result1);

    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP,  calculateExpectedValueGivenDurationsDefaultAggr(setDurations2[0]))
        .distributedByContains(getLocalisedUnassignedLabel(),  UNASSIGNED_TASK_DURATION)
      .doAssert(result2);
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

    importAndRefresh();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ReportConstants.ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), CoreMatchers.is((long) selectedTenants.size()));
  }

  @Test
  public void evaluateReportWithIrrationalNumberAsResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    Long[] setDurations = new Long[]{100L, 300L, 600L};
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[0]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[1]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[2]);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(3L)
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP, calculateExpectedValueGivenDurations(setDurations).get(aggType))
        .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("executionStates")
  public void evaluateReportWithExecutionState(final FlowNodeExecutionState executionState) {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, USER_TASK_1, 100L);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.claimAllRunningUserTasks(processInstanceDto.getId());
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_2, 700L);
    changeUserTaskClaimDate(processInstanceDto, now, USER_TASK_2, 500L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // claim first running task
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.claimAllRunningUserTasks(processInstanceDto2.getId());
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_1, 700L);
    changeUserTaskClaimDate(processInstanceDto2, now, USER_TASK_1, 500L);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setFlowNodeExecutionState(executionState);
    final ReportHyperMapResultDto actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getData().size(), is(2));
    assertEvaluateReportWithExecutionState(actualResult, executionState);
  }

  protected abstract void assertEvaluateReportWithExecutionState(final ReportHyperMapResultDto result,
                                                                 final FlowNodeExecutionState executionState);

  @Test
  public void processDefinitionContainsMultiInstanceBody() {
    // given
    BpmnModelInstance processWithMultiInstanceUserTask = Bpmn
      // @formatter:off
      .createExecutableProcess("processWithMultiInstanceUserTask")
      .startEvent()
      .userTask(USER_TASK_1).multiInstance().cardinality("2").multiInstanceDone()
      .endEvent()
      .done();
    // @formatter:on

    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        processWithMultiInstanceUserTask
      );
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 10L)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void evaluateReportForMoreThanTenEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();

    for (int i = 0; i < 11; i++) {
      final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
        processDefinition.getId());
      engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
      engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
      changeDuration(processInstanceDto, i);
    }

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(11L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 5L)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void filterInReport() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());

    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    final OffsetDateTime processStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstanceDto.getId())
        .getStartTime();

    importAndRefresh();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      evaluateHyperMapReport(reportData);

    // then
    ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    assertThat(actualResult.getData(), is(notNullValue()));
    assertThat(actualResult.getData().size(), is(1));

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    actualResult = evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getData(), is(notNullValue()));
    assertThat(actualResult.getData().size(), is(1));
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 10L)
      .doAssert(actualResult);
    // @formatter:on
  }

  private List<ProcessFilterDto> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(endDate).add().buildList();
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
    final Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  private void changeUserTaskStartDate(final ProcessInstanceEngineDto processInstanceDto,
                                       final OffsetDateTime now,
                                       final String userTaskId,
                                       final long offsetDuration) {
    try {
      engineDatabaseExtension.changeUserTaskStartDate(
        processInstanceDto.getId(),
        userTaskId,
        now.minus(offsetDuration, ChronoUnit.MILLIS)
      );
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  private void changeUserTaskClaimDate(final ProcessInstanceEngineDto processInstanceDto,
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

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final long duration);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration);

  protected abstract ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions);

  private ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return createReport(processDefinitionKey, newArrayList(version));
  }

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), processDefinition.getVersionAsString());
  }

  private void finishUserTaskOneWithFirstGroupAndLeaveOneUnassigned(final ProcessInstanceEngineDto processInstanceDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
  }

  private void finishUserTask1AWithFirstAndTaskB2WithSecondGroup(final ProcessInstanceEngineDto processInstanceDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    // finish user task 2 and B with second user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
  }

  private String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final ProcessDefinitionEngineDto processDefinitionEngineDto = deployOneUserTasksDefinition(processKey, tenant);
        engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
      });

    return processKey;
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition() {
    return deployOneUserTasksDefinition(PROCESS_DEFINITION_KEY, null);
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition(String key, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent()
      .parallelGateway()
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent()
      .moveToLastGateway()
      .userTask(USER_TASK_A)
      .userTask(USER_TASK_B)
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private Map<AggregationType, ReportHyperMapResultDto> evaluateHypeMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, ReportHyperMapResultDto> resultsMap = new HashMap<>();
    aggregationTypes.forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportHyperMapResultDto result = evaluateHyperMapReport(reportData).getResult();
      resultsMap.put(aggType, result);
    });
    return resultsMap;
  }

  protected String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }

  private void importAndRefresh() {
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }
}
