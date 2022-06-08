/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.usertask.distributedby.candidategroup;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Triple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.camunda.optimize.util.BpmnModels.DEFAULT_PROCESS_ID;
import static org.camunda.optimize.util.BpmnModels.START_EVENT_ID;
import static org.camunda.optimize.util.BpmnModels.VERSION_TAG;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public abstract class AbstractUserTaskDurationByUserTaskByCandidateGroupReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  protected static final String USER_TASK_1 = "userTask1";
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
    importAndRefresh();

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
    assertThat(resultReportDataDto.getDistributedBy().getType())
      .isEqualTo(DistributedByType.CANDIDATE_GROUP);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 20., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 20., SECOND_CANDIDATE_GROUP_NAME)
        .groupByContains(USER_TASK_A)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 20., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
        .groupByContains(USER_TASK_B)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 20., SECOND_CANDIDATE_GROUP_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessInstance_whenCandidateGroupCacheEmptyLabelEqualsKey() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

    changeDuration(processInstance, 1.);
    importAndRefresh();

    // cache is empty
    embeddedOptimizeExtension.getUserTaskIdentityCache().resetCache();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 1., FIRST_CANDIDATE_GROUP_ID)
      .doAssert(actualResult);
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
    importAndRefresh();

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
    assertThat(resultReportDataDto.getDistributedBy().getType())
      .isEqualTo(DistributedByType.CANDIDATE_GROUP);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    assertHyperMap_ForOneProcessInstanceWithUnassignedTasks(actualResult);
  }

  protected void assertHyperMap_ForOneProcessInstanceWithUnassignedTasks(final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_A)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_B)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
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
    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 10., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 20., FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 20., SECOND_CANDIDATE_GROUP_NAME)
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

    importAndRefresh();

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
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(
            SECOND_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]),
            SECOND_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_A)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
        .groupByContains(USER_TASK_B)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(
            SECOND_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]),
            SECOND_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
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

    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertHyperMap_ForSeveralProcessInstancesWithAllAggregationTypes(result);
  }

  protected void assertHyperMap_ForSeveralProcessInstancesWithAllAggregationTypes(
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result) {
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
        hyperMapAsserter
          .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
            .groupByContains(USER_TASK_1)
              .distributedByContains(
                FIRST_CANDIDATE_GROUP_ID,
                calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
                FIRST_CANDIDATE_GROUP_NAME
              )
              .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
              .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
            .groupByContains(USER_TASK_2)
              .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
              .distributedByContains(
                SECOND_CANDIDATE_GROUP_ID,
                calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType),
                SECOND_CANDIDATE_GROUP_NAME
              )
              .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
            .groupByContains(USER_TASK_A)
              .distributedByContains(
                FIRST_CANDIDATE_GROUP_ID,
                calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
                FIRST_CANDIDATE_GROUP_NAME
              )
              .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
              .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
            .groupByContains(USER_TASK_B)
              .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
              .distributedByContains(
                SECOND_CANDIDATE_GROUP_ID,
                calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType),
                SECOND_CANDIDATE_GROUP_NAME
              )
              .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
            .add()
          .add();
        // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
  }

  @Test
  public void reportEvaluationForSeveralProcessDefinitions() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";
    final Double expectedDuration = 20.;
    final ProcessDefinitionEngineDto processDefinition1 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key1, USER_TASK_1));
    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension
      .addCandidateGroupForAllRunningUserTasks(processInstanceDto1.getId(), FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    changeDuration(processInstanceDto1, expectedDuration);
    final ProcessDefinitionEngineDto processDefinition2 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram(key2, USER_TASK_2));
    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension
      .addCandidateGroupForAllRunningUserTasks(processInstanceDto2.getId(), SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto2.getId());
    changeDuration(processInstanceDto2, expectedDuration);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition1);
    reportData.getDefinitions().add(createReportDataDefinitionDto(key2));
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
      .groupByContains(USER_TASK_1)
      .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 20., FIRST_CANDIDATE_GROUP_NAME)
      .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
      .groupByContains(USER_TASK_2)
      .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
      .distributedByContains(SECOND_CANDIDATE_GROUP_ID, 20., SECOND_CANDIDATE_GROUP_NAME)
      .doAssert(result);
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
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertHyperMap_ForMultipleEventsWithAllAggregationTypes(result);
  }

  protected void assertHyperMap_ForMultipleEventsWithAllAggregationTypes(
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result) {
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
        hyperMapAsserter
          .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
            .groupByContains(USER_TASK_1)
              .distributedByContains(
                FIRST_CANDIDATE_GROUP_ID,
                calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
                FIRST_CANDIDATE_GROUP_NAME
              )
              .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
              .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, null, getLocalisedUnassignedLabel())
            .groupByContains(USER_TASK_2)
              .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
              .distributedByContains(
                SECOND_CANDIDATE_GROUP_ID,
                calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType),
                SECOND_CANDIDATE_GROUP_NAME
              )
              .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
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

    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(USER_TASK_2)
            .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
            .distributedByContains(SECOND_CANDIDATE_GROUP_ID, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), SECOND_CANDIDATE_GROUP_NAME)
          .groupByContains(USER_TASK_1)
            .distributedByContains(FIRST_CANDIDATE_GROUP_ID, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), FIRST_CANDIDATE_GROUP_NAME)
            .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .add()
        .add();
      // @formatter:on
    });
    hyperMapAsserter.doAssert(result);
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag(VERSION_TAG)
      .startEvent(START_EVENT_ID)
      .userTask(USER_TASK_1).name("thisLabelComesSecond")
      .userTask(USER_TASK_2).name("thisLabelComesFirst")
      .endEvent(END_EVENT)
      .done();

    final ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(bpmnModelInstance);

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
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_LABEL, SortOrder.DESC));
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1, "thisLabelComesSecond")
            .distributedByContains(FIRST_CANDIDATE_GROUP_ID, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), FIRST_CANDIDATE_GROUP_NAME)
            .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
          .groupByContains(USER_TASK_2, "thisLabelComesFirst")
            .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
            .distributedByContains(SECOND_CANDIDATE_GROUP_ID, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType), SECOND_CANDIDATE_GROUP_NAME)
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

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAndRefresh();

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
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(20., 40.),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID,  null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(
            SECOND_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(40.),
            SECOND_CANDIDATE_GROUP_NAME
          )
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

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAndRefresh();

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
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(20., 40.),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(SECOND_CANDIDATE_GROUP_ID, null, SECOND_CANDIDATE_GROUP_NAME)
        .groupByContains(USER_TASK_2)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, null, FIRST_CANDIDATE_GROUP_NAME)
          .distributedByContains(
            SECOND_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(40.),
            SECOND_CANDIDATE_GROUP_NAME
          )
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion()).isEqualTo(2);

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAndRefresh();

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
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(20., 40.),
            FIRST_CANDIDATE_GROUP_NAME
          )
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion()).isEqualTo(3);

    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);
    changeDuration(processInstanceDto1, 20.);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);
    changeDuration(processInstanceDto2, 40.);

    importAndRefresh();

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
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(20., 40.),
            FIRST_CANDIDATE_GROUP_NAME
          )
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

    importAndRefresh();

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
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(setDurations1),
            FIRST_CANDIDATE_GROUP_NAME
          )
      .doAssert(result1);

    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(
            FIRST_CANDIDATE_GROUP_ID,
            calculateExpectedValueGivenDurationsDefaultAggr(setDurations2[0]),
            FIRST_CANDIDATE_GROUP_NAME
          )
          .distributedByContains(DISTRIBUTE_BY_IDENTITY_MISSING_KEY, UNASSIGNED_TASK_DURATION, getLocalisedUnassignedLabel())
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
    ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
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

    importAndRefresh();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());

    // when
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L);
    Arrays.stream(getSupportedAggregationTypes()).forEach(aggType -> {
      // @formatter:off
      hyperMapAsserter
        .measure(ViewProperty.DURATION, aggType, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(
              FIRST_CANDIDATE_GROUP_ID,
              calculateExpectedValueGivenDurations(setDurations).get(aggType),
              FIRST_CANDIDATE_GROUP_NAME
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
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  private static Stream<Arguments> flowNodeStatusProcessFilters() {
    return Stream.of(
      Arguments.of(ProcessFilterBuilder.filter().runningFlowNodesOnly().add().buildList(), 2, 2L),
      Arguments.of(ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().add().buildList(), 1, 1L),
      Arguments.of(ProcessFilterBuilder.filter().canceledFlowNodesOnly().add().buildList(), 2, 2L)
    );
  }

  @ParameterizedTest
  @MethodSource("flowNodeStatusProcessFilters")
  public void evaluateReportWithFlowNodeStatus(final List<ProcessFilterDto<?>> processFilter,
                                               final Integer expectedDataSize,
                                               final long expectedInstanceCount) {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(firstInstance.getId());
    changeDuration(firstInstance, USER_TASK_1, 100.);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.claimAllRunningUserTasks(firstInstance.getId());
    if (isSingleFilterOfType(processFilter, CanceledFlowNodesOnlyFilterDto.class)) {
      engineIntegrationExtension.cancelActivityInstance(firstInstance.getId(), USER_TASK_2);
      changeDuration(firstInstance, USER_TASK_2, 700.);
    } else {
      changeUserTaskStartDate(firstInstance, now, USER_TASK_2, 700.);
      changeUserTaskClaimDate(firstInstance, now, USER_TASK_2, 500.);
    }

    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // claim first running task
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.claimAllRunningUserTasks(secondInstance.getId());
    if (isSingleFilterOfType(processFilter, CanceledFlowNodesOnlyFilterDto.class)) {
      engineIntegrationExtension.cancelActivityInstance(secondInstance.getId(), USER_TASK_1);
      changeDuration(secondInstance, USER_TASK_1, 700.);
    } else {
      changeUserTaskStartDate(secondInstance, now, USER_TASK_1, 700.);
      changeUserTaskClaimDate(secondInstance, now, USER_TASK_1, 500.);
    }

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(processFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    assertThat(actualResult.getFirstMeasureData()).hasSize(expectedDataSize);
    assertEvaluateReportWithFlowNodeStatusFilter(actualResult, processFilter, expectedInstanceCount);
  }

  protected abstract void assertEvaluateReportWithFlowNodeStatusFilter(final ReportResultResponseDto<List<HyperMapResultEntryDto>> result,
                                                                       final List<ProcessFilterDto<?>> executionState,
                                                                       final long expectedInstanceCount);

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

    importAndRefresh();

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
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 10., FIRST_CANDIDATE_GROUP_NAME)
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

    importAndRefresh();

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
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 5., FIRST_CANDIDATE_GROUP_NAME)
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

    importAndRefresh();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = evaluationResponse.getResult();
    assertThat(actualResult.getFirstMeasureData()).isNotNull();
    assertThat(actualResult.getFirstMeasureData()).hasSize(1);

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getFirstMeasureData()).isNotNull().hasSize(1);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(USER_TASK_1)
          .distributedByContains(FIRST_CANDIDATE_GROUP_ID, 10., FIRST_CANDIDATE_GROUP_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  private List<ProcessFilterDto<?>> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedInstanceStartDate().start(startDate).end(endDate).add().buildList();
  }

  public static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_2, Collections.singletonList(createSecondGroupTriple(10.)))
          .build()
      ),
      Arguments.of(
        IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(10.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(null), createSecondGroupTriple(10.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Collections.singletonList(createFirstGroupTriple(10.)))
          .build()
      ),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L, Collections.emptyMap())
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  public void viewLevelFilterByAssigneeOnlyIncludesUserTaskWithThatAssignee(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId());

    changeDuration(processInstanceDto, USER_TASK_1, 10.);
    changeDuration(processInstanceDto, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW).add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    final HyperMapAsserter.MeasureAdder hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(expectedInstanceCount)
      .processInstanceCountWithoutFilters(1)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime());
    expectedResult.forEach((userTaskId, distributionResults) -> {
      final HyperMapAsserter.GroupByAdder groupByAdder = hyperMapAsserter.groupByContains(userTaskId);
      distributionResults.forEach(
        candidateGroupAndCount -> groupByAdder.distributedByContains(
          candidateGroupAndCount.getLeft(), candidateGroupAndCount.getMiddle(), candidateGroupAndCount.getRight()
        )
      );
      groupByAdder.add();
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  public static Stream<Arguments> instanceLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_USER}, 1L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(10.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(null), createSecondGroupTriple(10.))
          )
          .build()
      ),
      Arguments.of(
        IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 2L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(15.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(30.), createSecondGroupTriple(10.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_USER}, 2L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(15.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(30.), createSecondGroupTriple(10.))
          )
          .build()
      ),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L, ImmutableMap.builder()
        .put(USER_TASK_1, Collections.emptyList())
        .put(USER_TASK_2, Collections.emptyList())
        .build()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelAssigneeFilterScenarios")
  public void instanceLevelFilterByAssigneeOnlyIncludesUserTaskForInstancesWithThatAssignee(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, firstInstance.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, firstInstance.getId());
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());

    changeDuration(firstInstance, USER_TASK_1, 10.);
    changeDuration(firstInstance, USER_TASK_2, 10.);
    changeDuration(secondInstance, USER_TASK_1, 20.);
    changeDuration(secondInstance, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.INSTANCE).add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    final HyperMapAsserter.MeasureAdder hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(expectedInstanceCount)
      .processInstanceCountWithoutFilters(2)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime());
    expectedResult.forEach((userTaskId, distributionResults) -> {
      final HyperMapAsserter.GroupByAdder groupByAdder = hyperMapAsserter.groupByContains(userTaskId);
      distributionResults.forEach(
        candidateGroupAndCount -> groupByAdder.distributedByContains(
          candidateGroupAndCount.getLeft(), candidateGroupAndCount.getMiddle(), candidateGroupAndCount.getRight()
        )
      );
      groupByAdder.add();
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  public static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder()
          .put(USER_TASK_2, Collections.singletonList(createSecondGroupTriple(10.)))
          .build()
      ),
      Arguments.of(
        IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(10.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(null), createSecondGroupTriple(10.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder().put(USER_TASK_1, Collections.singletonList(createFirstGroupTriple(10.)))
          .build()
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        0L,
        Collections.emptyMap()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  public void viewLevelFilterByCandidateGroupOnlyIncludesUserTaskWithThatCandidateGroup(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

    changeDuration(processInstanceDto, USER_TASK_1, 10.);
    changeDuration(processInstanceDto, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    final HyperMapAsserter.MeasureAdder hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(expectedInstanceCount)
      .processInstanceCountWithoutFilters(1)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime());
    expectedResult.forEach((userTaskId, distributionResults) -> {
      final HyperMapAsserter.GroupByAdder groupByAdder = hyperMapAsserter.groupByContains(userTaskId);
      distributionResults.forEach(
        candidateGroupAndCount -> groupByAdder.distributedByContains(
          candidateGroupAndCount.getLeft(), candidateGroupAndCount.getMiddle(), candidateGroupAndCount.getRight()
        )
      );
      groupByAdder.add();
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  public static Stream<Arguments> instanceLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(10.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(null), createSecondGroupTriple(10.))
          )
          .build()
      ),
      Arguments.of(
        IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 2L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(15.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(30.), createSecondGroupTriple(10.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 2L,
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Arrays.asList(createFirstGroupTriple(15.), createSecondGroupTriple(null))
          )
          .put(
            USER_TASK_2,
            Arrays.asList(createFirstGroupTriple(30.), createSecondGroupTriple(10.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 0L,
        ImmutableMap.builder()
          .put(USER_TASK_1, Collections.emptyList())
          .put(USER_TASK_2, Collections.emptyList())
          .build()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelCandidateGroupFilterScenarios")
  public void instanceLevelFilterByCandidateGroupOnlyIncludesUserTaskFromInstancesWithThatCandidateGroup(
    final MembershipFilterOperator filterOperator,
    final String[] filterValues,
    final Long expectedInstanceCount,
    final Map<String, List<Triple<String, Double, String>>> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

    changeDuration(firstInstance, USER_TASK_1, 10.);
    changeDuration(firstInstance, USER_TASK_2, 10.);
    changeDuration(secondInstance, USER_TASK_1, 20.);
    changeDuration(secondInstance, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.INSTANCE).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> actualResult = reportClient.evaluateHyperMapReport(
      reportData).getResult();

    // then
    final HyperMapAsserter.MeasureAdder hyperMapAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(expectedInstanceCount)
      .processInstanceCountWithoutFilters(2)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime());
    expectedResult.forEach((userTaskId, distributionResults) -> {
      final HyperMapAsserter.GroupByAdder groupByAdder = hyperMapAsserter.groupByContains(userTaskId);
      distributionResults.forEach(
        candidateGroupAndCount -> groupByAdder.distributedByContains(
          candidateGroupAndCount.getLeft(), candidateGroupAndCount.getMiddle(), candidateGroupAndCount.getRight()
        )
      );
      groupByAdder.add();
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(DEFAULT_PROCESS_ID, "1");
    dataDto.getView().setEntity(null);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(DEFAULT_PROCESS_ID, "1");
    dataDto.getView().setProperties((ViewProperty) null);

    // when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(DEFAULT_PROCESS_ID, "1");
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
    return deployOneUserTasksDefinition(DEFAULT_PROCESS_ID, null);
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition(String key, String tenantId) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram(key), tenantId);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram());
  }

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(DEFAULT_PROCESS_ID)
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

  protected String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }

  private void importAndRefresh() {
    importAllEngineEntitiesFromScratch();
  }
}
