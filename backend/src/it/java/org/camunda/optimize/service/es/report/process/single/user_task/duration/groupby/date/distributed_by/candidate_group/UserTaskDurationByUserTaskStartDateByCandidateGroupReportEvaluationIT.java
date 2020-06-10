/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.date.distributed_by.candidate_group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;

public abstract class UserTaskDurationByUserTaskStartDateByCandidateGroupReportEvaluationIT
  extends UserTaskDurationByUserTaskDateByCandidateGroupReportEvaluationIT {

  @Test
  public void reportEvaluationForOneProcessWithUnassignedTasks() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    final List<String> collect = result.getData().stream()
      .flatMap(entry -> entry.getValue().stream())
      .map(MapResultEntryDto::getKey)
      .collect(Collectors.toList());
    assertThat(collect).contains(getLocalisedUnassignedLabel());
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("getExecutionStateExpectedValues")
  public void evaluateReportWithExecutionState(final FlowNodeExecutionState executionState,
                                               final ExecutionStateTestValues candidateGroup1Count,
                                               final ExecutionStateTestValues candidateGroup2Count) {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());
    changeDuration(processInstance1, USER_TASK_1, 100L);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.claimAllRunningUserTasks(processInstance1.getId());
    changeUserTaskStartDate(processInstance1, now, USER_TASK_2, 700L);
    changeUserTaskClaimDate(processInstance1, now, USER_TASK_2, 500L);

    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    // claim first running task
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(processInstance2.getId(), FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.claimAllRunningUserTasks(processInstance2.getId());
    changeUserTaskStartDate(processInstance2, now, USER_TASK_1, 700L);
    changeUserTaskClaimDate(processInstance2, now, USER_TASK_1, 500L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.DAY);
    reportData.getConfiguration().setFlowNodeExecutionState(executionState);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final HyperMapAsserter.GroupByAdder groupByAsserter = HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()));
    if (candidateGroup2Count != null) {
      groupByAsserter.distributedByContains(SECOND_CANDIDATE_GROUP, getCorrectTestExecutionValue(candidateGroup2Count));
    }
    if (candidateGroup1Count != null) {
      groupByAsserter.distributedByContains(FIRST_CANDIDATE_GROUP, getCorrectTestExecutionValue(candidateGroup1Count));
    }
    groupByAsserter.doAssert(result);
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  static class ExecutionStateTestValues {
    Long expectedIdleDurationValue;
    Long expectedWorkDurationValue;
    Long expectedTotalDurationValue;
  }

  protected static Stream<Arguments> getExecutionStateExpectedValues() {
    return Stream.of(
      Arguments.of(
        FlowNodeExecutionState.RUNNING,
        new ExecutionStateTestValues(200L, 500L, 700L),
        new ExecutionStateTestValues(200L, 500L, 700L)
      ),
      Arguments.of(
        FlowNodeExecutionState.COMPLETED,
        new ExecutionStateTestValues(100L, 100L, 100L),
        null
      ),
      Arguments.of(
        FlowNodeExecutionState.ALL,
        new ExecutionStateTestValues(
          calculateExpectedValueGivenDurationsDefaultAggr(100L, 200L),
          calculateExpectedValueGivenDurationsDefaultAggr(100L, 500L),
          calculateExpectedValueGivenDurationsDefaultAggr(100L, 700L)
        ),
        new ExecutionStateTestValues(
          calculateExpectedValueGivenDurationsDefaultAggr(200L),
          calculateExpectedValueGivenDurationsDefaultAggr(500L),
          calculateExpectedValueGivenDurationsDefaultAggr(700L)
        )
      )
    );
  }

  private String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }

  protected abstract Long getCorrectTestExecutionValue(final ExecutionStateTestValues executionStateTestValues);

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP;
  }

  @Override
  protected void changeUserTaskDates(final Map<String, OffsetDateTime> updates) {
    engineDatabaseExtension.changeUserTaskStartDates(updates);
  }

  @Override
  protected void changeUserTaskDate(final ProcessInstanceEngineDto processInstance,
                                    final String userTaskKey,
                                    final OffsetDateTime dateToChangeTo) {
    engineDatabaseExtension.changeUserTaskStartDate(processInstance.getId(), userTaskKey, dateToChangeTo);
  }
}
