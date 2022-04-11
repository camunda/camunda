/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.frequency.groupby.date.distributedby.none;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.ModelElementFrequencyByModelElementDateReportEvaluationIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;

public abstract class UserTaskFrequencyByUserTaskDateReportEvaluationIT
  extends ModelElementFrequencyByModelElementDateReportEvaluationIT {

  public static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{SECOND_USER}, 1L, 1.),
      Arguments.of(IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 1L, 2.),
      Arguments.of(NOT_IN, new String[]{SECOND_USER}, 1L, 1.),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L, null)
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  public void viewLevelFilterByAssigneeOnlyCountsUserTasksWithThatAssignee(final MembershipFilterOperator filterOperator,
                                                                           final String[] filterValues,
                                                                           final Long expectedInstanceCount,
                                                                           final Double expectedUserTaskCount) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

    final ProcessDefinitionEngineDto processDefinition = deployTwoModelElementDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW).add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    if (expectedUserTaskCount != null) {
      assertThat(result.getFirstMeasureData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getFirstMeasureData()).isEmpty();
    }
  }

  public static Stream<Arguments> instanceLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{SECOND_USER}, 1L, 2.),
      Arguments.of(IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 2L, 4.),
      Arguments.of(NOT_IN, new String[]{SECOND_USER}, 2L, 4.),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L, null)
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelAssigneeFilterScenarios")
  public void instanceLevelFilterByAssigneeOnlyCountsUserTasksFromInstancesWithThatAssignee(final MembershipFilterOperator filterOperator,
                                                                                            final String[] filterValues,
                                                                                            final Long expectedInstanceCount,
                                                                                            final Double expectedUserTaskCount) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

    final ProcessDefinitionEngineDto processDefinition = deployTwoModelElementDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, firstInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, firstInstance.getId());
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.INSTANCE).add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    if (expectedUserTaskCount != null) {
      assertThat(result.getFirstMeasureData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getFirstMeasureData()).isEmpty();
    }
  }

  public static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L, 1.),
      Arguments.of(IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 1L, 2.),
      Arguments.of(NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L, 1.),
      Arguments.of(NOT_IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 0L, null)
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  public void viewLevelFilterByCandidateGroupOnlyCountsUserTasksWithThatCandidateGroup(final MembershipFilterOperator filterOperator,
                                                                                       final String[] filterValues,
                                                                                       final Long expectedInstanceCount,
                                                                                       final Double expectedUserTaskCount) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID);

    final ProcessDefinitionEngineDto processDefinition = deployTwoModelElementDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator).
        filterLevel(FilterApplicationLevel.VIEW).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    if (expectedUserTaskCount != null) {
      assertThat(result.getFirstMeasureData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getFirstMeasureData()).isEmpty();
    }
  }

  public static Stream<Arguments> instanceLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L, 2.),
      Arguments.of(IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 2L, 4.),
      Arguments.of(NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 2L, 4.),
      Arguments.of(NOT_IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 0L, null)
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelCandidateGroupFilterScenarios")
  public void instanceLevelFilterByCandidateGroupOnlyCountsUserTasksFromInstancesWithThatCandidateGroup(final MembershipFilterOperator filterOperator,
                                                                                                        final String[] filterValues,
                                                                                                        final Long expectedInstanceCount,
                                                                                                        final Double expectedUserTaskCount) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID);

    final ProcessDefinitionEngineDto processDefinition = deployTwoModelElementDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator).
        filterLevel(FilterApplicationLevel.INSTANCE).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    if (expectedUserTaskCount != null) {
      assertThat(result.getFirstMeasureData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getFirstMeasureData()).isEmpty();
    }
  }

  public static Stream<Arguments> viewLevelFlowNodeDurationFilterScenarios() {
    return Stream.of(
      Arguments.of(USER_TASK_1, GREATER_THAN, 1000L, 1L, 1.),
      Arguments.of(USER_TASK_2, GREATER_THAN, 500L, 1L, 1.),
      Arguments.of(USER_TASK_1, GREATER_THAN_EQUALS, 2000L, 1L, 1.),
      Arguments.of(USER_TASK_1, LESS_THAN_EQUALS, 2000L, 1L, 1.),
      Arguments.of(USER_TASK_2, LESS_THAN_EQUALS, 1000L, 1L, 1.),
      Arguments.of(USER_TASK_1, LESS_THAN, 2000L, 0L, null)
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelFlowNodeDurationFilterScenarios")
  public void viewLevelFilterByFlowNodeDurationOnlyCountsUserTasksFromInstancesMatchingFilter(final String userTaskId,
                                                                                              final ComparisonOperator filterOperator,
                                                                                              final Long filterValueInMs,
                                                                                              final Long expectedInstanceCount,
                                                                                              final Double expectedUserTaskCount) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoModelElementDefinition();
    final ProcessInstanceEngineDto instance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    // We have to change both durations as the instance level filtering applies to the activities and the
    // view level filtering applies to the user tasks
    engineDatabaseExtension.changeFlowNodeTotalDuration(instance.getId(), USER_TASK_1, 2000.);
    engineDatabaseExtension.changeFlowNodeTotalDuration(instance.getId(), USER_TASK_2, 1000.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> flowNodeDurationFilter = ProcessFilterBuilder.filter()
      .flowNodeDuration()
      .flowNode(userTaskId, DurationFilterDataDto.builder().unit(DurationUnit.MILLIS)
        .value(filterValueInMs).operator(filterOperator).build())
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    reportData.setFilter(flowNodeDurationFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    if (expectedUserTaskCount != null) {
      assertThat(result.getFirstMeasureData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getFirstMeasureData()).isEmpty();
    }
  }

  @Test
  public void automaticIntervalSelection_forOneDataPoint() {
    // given there is only one data point
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then the single data point should be grouped by month
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(1);
    ZonedDateTime nowStrippedToMonth = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS);
    String nowStrippedToMonthAsString = localDateTimeToString(nowStrippedToMonth);
    assertThat(resultData).first().extracting(MapResultEntryDto::getKey).isEqualTo(nowStrippedToMonthAsString);
    assertThat(resultData).first().extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  private void finishAllUserTasks(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish first task
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    // finish second task
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
  }

  @Override
  protected void startInstancesWithDayRangeForDefinition(ProcessDefinitionEngineDto processDefinition,
                                                         ZonedDateTime min,
                                                         ZonedDateTime max) {
    ProcessInstanceEngineDto procInstMin = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto procInstMax = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(procInstMin, USER_TASK_1, min.toOffsetDateTime());
    changeModelElementDate(procInstMax, USER_TASK_1, max.toOffsetDateTime());
  }

  @Override
  protected ProcessDefinitionEngineDto deployTwoModelElementDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram());
  }

  @Override
  protected ProcessDefinitionEngineDto deploySimpleModelElementDefinition() {
    return deployOneUserTaskDefinition();
  }

  @Override
  protected ProcessInstanceEngineDto startAndCompleteInstance(String definitionId) {
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(definitionId);
    finishAllUserTasks(processInstanceDto);
    return processInstanceDto;
  }

  @Override
  protected ProcessInstanceEngineDto startAndCompleteInstanceWithDates(String definitionId,
                                                                       OffsetDateTime firstElementDate,
                                                                       OffsetDateTime secondElementDate) {
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(definitionId);
    finishAllUserTasks(processInstanceDto);
    changeModelElementDate(processInstanceDto, USER_TASK_1, firstElementDate);
    changeModelElementDate(processInstanceDto, USER_TASK_2, secondElementDate);
    return processInstanceDto;
  }

  @Override
  protected ProcessViewEntity getExpectedViewEntity() {
    return ProcessViewEntity.USER_TASK;
  }

}
