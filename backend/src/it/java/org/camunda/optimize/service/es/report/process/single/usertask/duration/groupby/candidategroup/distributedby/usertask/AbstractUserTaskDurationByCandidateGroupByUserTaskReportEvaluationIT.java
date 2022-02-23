/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.candidategroup.distributedby.usertask;

import com.google.common.collect.ImmutableList;
import lombok.Data;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;

public abstract class AbstractUserTaskDurationByCandidateGroupByUserTaskReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String PROCESS_DEFINITION_KEY = "123";
  protected static final String USER_TASK_1 = "userTask1";
  protected static final String USER_TASK_1_NAME = "userTask1Name";
  protected static final String USER_TASK_2_NAME = "userTask2Name";
  protected static final String USER_TASK_2 = "userTask2";
  protected static final String USER_TASK_A = "userTaskA";
  protected static final String USER_TASK_B = "userTaskB";
  private static final Double UNASSIGNED_TASK_DURATION = 500.;
  protected static final Double[] SET_DURATIONS = new Double[]{10., 20.};

  @BeforeEach
  public void init() {
    // create second user
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME);
  }

  @Test
  public void reportEvaluationForOneProcessInstance() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto);

    final Double setDuration = 20.;
    changeDuration(processInstanceDto, setDuration);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTimes())
      .containsExactly(getUserTaskDurationTime());
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(DistributedByType.USER_TASK);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, 20.)
          .distributedByContains(USER_TASK_2, null)
          .distributedByContains(USER_TASK_A, 20.)
          .distributedByContains(USER_TASK_B, null)
        .groupByContains(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, null)
          .distributedByContains(USER_TASK_2, 20.)
          .distributedByContains(USER_TASK_A, null)
          .distributedByContains(USER_TASK_B, 20.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessInstance_whenCandidateCacheEmptyLabelEqualsKey() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

    changeDuration(processInstanceDto, 1.);
    importAllEngineEntitiesFromScratch();

    // cache is empty
    embeddedOptimizeExtension.getUserTaskIdentityCache().resetCache();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_ID)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessInstanceWithUnassignedTasks() {
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
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.USER_TASK);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTimes())
      .containsExactly(getUserTaskDurationTime());
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(DistributedByType.USER_TASK);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    assertHyperMap_ForOneProcessInstanceWithUnassignedTasks(actualResult);
  }

  protected void assertHyperMap_ForOneProcessInstanceWithUnassignedTasks(final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
          .distributedByContains(USER_TASK_2, null)
          .distributedByContains(USER_TASK_A, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
          .distributedByContains(USER_TASK_B, null)
        .groupByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, getLocalisedUnassignedLabel())
          .distributedByContains(USER_TASK_1, null)
          .distributedByContains(USER_TASK_2, UNASSIGNED_TASK_DURATION)
          .distributedByContains(USER_TASK_A, null)
          .distributedByContains(USER_TASK_B, UNASSIGNED_TASK_DURATION)
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
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    // finish second task with
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());

    changeDuration(processInstanceDto, USER_TASK_1, 10.);
    changeDuration(processInstanceDto, USER_TASK_2, 20.);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 20., USER_TASK_2_NAME)
        .groupByContains(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 20., USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessDefinitions() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";

    final ProcessDefinitionEngineDto processDefinition1 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key1, USER_TASK_1));
    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension
      .addCandidateGroupForAllRunningUserTasks(processInstanceDto1.getId(), FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    changeDuration(processInstanceDto1, SET_DURATIONS[0]);
    final ProcessDefinitionEngineDto processDefinition2 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key2, USER_TASK_2));
    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension
      .addCandidateGroupForAllRunningUserTasks(processInstanceDto2.getId(), SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto2.getId());
    changeDuration(processInstanceDto2, SET_DURATIONS[1]);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition1);
    reportData.getDefinitions().add(createReportDataDefinitionDto(key2));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
      .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
      .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
      .distributedByContains(USER_TASK_2, null)
      .groupByContains(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME)
      .distributedByContains(USER_TASK_1, null)
      .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessInstances() {
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

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    assertHyperMap_ForSeveralProcessInstances(actualResult);
  }

  protected void assertHyperMap_ForSeveralProcessInstances(final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1,calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
          .distributedByContains(USER_TASK_2, null)
          .distributedByContains(USER_TASK_A, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
          .distributedByContains(USER_TASK_B, null)
        .groupByContains(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, null)
          .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
          .distributedByContains(USER_TASK_A, null)
          .distributedByContains(USER_TASK_B, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
        .groupByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, getLocalisedUnassignedLabel())
          .distributedByContains(USER_TASK_1, null)
          .distributedByContains(USER_TASK_2, UNASSIGNED_TASK_DURATION)
          .distributedByContains(USER_TASK_A, null)
          .distributedByContains(USER_TASK_B, UNASSIGNED_TASK_DURATION)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessInstancesWithAllAggregationTypes() {
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

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertHyperMap_ForSeveralProcessInstancesWithAllAggregationTypes(result);
  }

  protected void assertHyperMap_ForSeveralProcessInstancesWithAllAggregationTypes(
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult) {
    Assertions.assertThat(actualResult.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
            .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
            .distributedByContains(USER_TASK_2, null)
            .distributedByContains(USER_TASK_A, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
            .distributedByContains(USER_TASK_B, null)
          .groupByContains(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME)
            .distributedByContains(USER_TASK_1, null)
            .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
            .distributedByContains(USER_TASK_A, null)
            .distributedByContains(USER_TASK_B, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
          .groupByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, getLocalisedUnassignedLabel())
            .distributedByContains(USER_TASK_1, null)
            .distributedByContains(USER_TASK_2, UNASSIGNED_TASK_DURATION)
            .distributedByContains(USER_TASK_A, null)
            .distributedByContains(USER_TASK_B, UNASSIGNED_TASK_DURATION)
          .add()
        .add();
      // @formatter:on
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[1]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskOneWithFirstGroupAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[0]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    assertHyperMap_ForMultipleEvents(actualResult);
  }

  protected void assertHyperMap_ForMultipleEvents(final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, SET_DURATIONS[0], USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .groupByContains(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, SET_DURATIONS[1], USER_TASK_2_NAME)
        .groupByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, getLocalisedUnassignedLabel())
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, UNASSIGNED_TASK_DURATION, USER_TASK_2_NAME)
      .doAssert(actualResult);
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

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();


    // then
    assertHyperMap_ForMultipleEventsWithAllAggregationTypes(result);
  }

  protected void assertHyperMap_ForMultipleEventsWithAllAggregationTypes(
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result) {
    // @formatter:off
    Assertions.assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
            .distributedByContains(
              USER_TASK_1, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), USER_TASK_1_NAME
            )
            .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
          .groupByContains(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME)
            .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
            .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), USER_TASK_2_NAME)
          .groupByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, getLocalisedUnassignedLabel())
            .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
            .distributedByContains(USER_TASK_2, UNASSIGNED_TASK_DURATION, USER_TASK_2_NAME)
          .add()
        .add();
      // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
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

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    Assertions.assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME)
            .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
            .distributedByContains(
              USER_TASK_2,
              calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
              USER_TASK_2_NAME
            )
          .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
            .distributedByContains(
              USER_TASK_1,
              calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
              USER_TASK_1_NAME
            )
            .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
          .add()
        .add();
      // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
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

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_LABEL, SortOrder.DESC));
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    Assertions.assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME)
            .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
            .distributedByContains(
              USER_TASK_2,
              calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
              USER_TASK_2_NAME
            )
          .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
            .distributedByContains(
              USER_TASK_1,
              calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
              USER_TASK_1_NAME
            )
            .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
          .add()
        .add();
      // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion()).isEqualTo(2);

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .groupByContains(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurationsDefaultAggr(40.), USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion()).isEqualTo(3);

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .groupByContains(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurationsDefaultAggr(40.), USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion()).isEqualTo(2);

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion()).isEqualTo(3);

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension
      .startProcessInstance(firstDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension
      .startProcessInstance(latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), USER_TASK_1_NAME)
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
    final Double[] setDurations1 = new Double[]{40., 20.};
    final Double[] setDurations2 = new Double[]{60., 80.};

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

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult1 = reportClient.evaluateHyperMapReport(
      reportData1).getResult();
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult2 = reportClient.evaluateHyperMapReport(
      reportData2).getResult();

    // then
    assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(
      setDurations1,
      setDurations2,
      actualResult1,
      actualResult2
    );
  }

  protected void assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(final Double[] setDurations1,
                                                                            final Double[] setDurations2,
                                                                            final ReportResultResponseDto<List<HyperMapResultEntryDto>> result1,
                                                                            final ReportResultResponseDto<List<HyperMapResultEntryDto>> result2) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(setDurations1), USER_TASK_1_NAME)
      .doAssert(result1);

    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(setDurations2[0]), USER_TASK_1_NAME)
        .groupByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, getLocalisedUnassignedLabel())
          .distributedByContains(USER_TASK_1, UNASSIGNED_TASK_DURATION, USER_TASK_1_NAME)
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

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ReportConstants.ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void evaluateReportWithIrrationalNumberAsResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    Double[] setDurations = new Double[]{100., 300., 600.};
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

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    Assertions.assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
            .distributedByContains(
            USER_TASK_1, calculateExpectedValueGivenDurations(setDurations).get(aggType),
            USER_TASK_1_NAME
          )
        .add()
        .add();
      // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData()).hasSize(0);
  }

  @Data
  static class FlowNodeStatusTestValues {
    List<ProcessFilterDto<?>> processFilters;
    HyperMapResultEntryDto expectedIdleDurationValues;
    HyperMapResultEntryDto expectedWorkDurationValues;
    HyperMapResultEntryDto expectedTotalDurationValues;
  }

  private static HyperMapResultEntryDto getExpectedResultsMap(Double userTask1Result, Double userTask2Result) {
    List<MapResultEntryDto> groupByResults = new ArrayList<>();
    MapResultEntryDto firstUserTask = new MapResultEntryDto(USER_TASK_1, userTask1Result, USER_TASK_1_NAME);
    groupByResults.add(firstUserTask);
    MapResultEntryDto secondUserTask = new MapResultEntryDto(USER_TASK_2, userTask2Result, USER_TASK_2_NAME);
    groupByResults.add(secondUserTask);
    return new HyperMapResultEntryDto(FIRST_CANDIDATE_GROUP_ID, groupByResults, FIRST_CANDIDATE_GROUP_NAME);
  }

  protected static Stream<FlowNodeStatusTestValues> getFlowNodeStatusExpectedValues() {
    FlowNodeStatusTestValues runningStateValues =
      new FlowNodeStatusTestValues();
    runningStateValues.processFilters = ProcessFilterBuilder.filter().runningFlowNodesOnly().add().buildList();
    runningStateValues.expectedIdleDurationValues = getExpectedResultsMap(200., 200.);
    runningStateValues.expectedWorkDurationValues = getExpectedResultsMap(500., 500.);
    runningStateValues.expectedTotalDurationValues = getExpectedResultsMap(700., 700.);

    FlowNodeStatusTestValues completedStateValues = new FlowNodeStatusTestValues();
    completedStateValues.processFilters = ProcessFilterBuilder.filter()
      .completedOrCanceledFlowNodesOnly().add().buildList();
    completedStateValues.expectedIdleDurationValues = getExpectedResultsMap(100., null);
    completedStateValues.expectedWorkDurationValues = getExpectedResultsMap(100., null);
    completedStateValues.expectedTotalDurationValues = getExpectedResultsMap(100., null);

    FlowNodeStatusTestValues completedOrCanceledStateValues = new FlowNodeStatusTestValues();
    completedOrCanceledStateValues.processFilters = ProcessFilterBuilder.filter()
      .completedOrCanceledFlowNodesOnly().add().buildList();
    completedOrCanceledStateValues.expectedIdleDurationValues = getExpectedResultsMap(100., null);
    completedOrCanceledStateValues.expectedWorkDurationValues = getExpectedResultsMap(100., null);
    completedOrCanceledStateValues.expectedTotalDurationValues = getExpectedResultsMap(100., null);

    return Stream.of(runningStateValues, completedStateValues, completedOrCanceledStateValues);
  }

  @ParameterizedTest
  @MethodSource("getFlowNodeStatusExpectedValues")
  public void evaluateReportWithFlowNodeStatusFilter(FlowNodeStatusTestValues flowNodeStatusTestValues) {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, USER_TASK_1, 100.);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.claimAllRunningUserTasks(processInstanceDto.getId());
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_2, 700.);
    changeUserTaskClaimDate(processInstanceDto, now, USER_TASK_2, 500.);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // claim first running task
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.claimAllRunningUserTasks(processInstanceDto2.getId());
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_1, 700.);
    changeUserTaskClaimDate(processInstanceDto2, now, USER_TASK_1, 500.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(flowNodeStatusTestValues.processFilters);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    assertThat(actualResult.getFirstMeasureData()).hasSize(1);
    assertEvaluateReportWithFlowNodeStatusFilters(actualResult, flowNodeStatusTestValues);
  }

  protected abstract void assertEvaluateReportWithFlowNodeStatusFilters(ReportResultResponseDto<List<HyperMapResultEntryDto>> result,
                                                                        FlowNodeStatusTestValues expectedValues);

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
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, 10.)
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
      engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
      engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
      changeDuration(processInstanceDto, (double) i);
    }

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(11L)
      .processInstanceCountWithoutFilters(11L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, 5., USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void filterInReport() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());

    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10.);

    final OffsetDateTime processStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstanceDto.getId())
        .getStartTime();

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    assertThat(actualResult.getFirstMeasureData()).isNotNull();
    assertThat(actualResult.getFirstMeasureData()).hasSize(0);

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getFirstMeasureData()).isNotNull();
    assertThat(actualResult.getFirstMeasureData()).hasSize(1);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE), getUserTaskDurationTime())
        .groupByContains(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  private List<ProcessFilterDto<?>> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedInstanceStartDate().start(startDate).end(endDate).add().buildList();
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperties((ViewProperty) null);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final Double durationInMs);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs);

  protected abstract ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions);

  private ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return createReport(processDefinitionKey, newArrayList(version));
  }

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), processDefinition.getVersionAsString());
  }


  private void finishUserTaskOneWithFirstGroupAndLeaveOneUnassigned(final ProcessInstanceEngineDto processInstanceDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
  }

  private void finishUserTask1AWithFirstAndTaskB2WithSecondGroup(final ProcessInstanceEngineDto processInstanceDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    // finish user task 2 and B with second user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
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
    return deployOneUserTasksDefinition("aProcess", null);
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition(String key, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .name(USER_TASK_1_NAME)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .name(USER_TASK_1_NAME)
      .userTask(USER_TASK_2)
      .name(USER_TASK_2_NAME)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
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

  private String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }
}
