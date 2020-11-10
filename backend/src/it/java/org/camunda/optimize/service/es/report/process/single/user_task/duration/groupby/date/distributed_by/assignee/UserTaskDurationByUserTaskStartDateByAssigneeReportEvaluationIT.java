/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.date.distributed_by.assignee;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;

public abstract class UserTaskDurationByUserTaskStartDateByAssigneeReportEvaluationIT
  extends UserTaskDurationByUserTaskDateByAssigneeReportEvaluationIT {

  @Test
  public void reportEvaluationForOneProcessWithUnassignedTasks() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);

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
                                               final ExecutionStateTestValues assignee2Count) {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    changeUserTaskDate(processInstance1, USER_TASK_1, now);
    changeDuration(processInstance1, USER_TASK_1, 100.);

    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.claimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstance2.getId());
    if (FlowNodeExecutionState.CANCELED.equals(executionState)) {
      engineIntegrationExtension.cancelActivityInstance(processInstance2.getId(), USER_TASK_1);
      changeDuration(processInstance2, USER_TASK_1, 100.);
    } else {
      changeUserTaskStartDate(processInstance2, now, USER_TASK_1, 700.);
      changeUserTaskClaimDate(processInstance2, now, USER_TASK_1, 500.);
    }

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.DAY);
    reportData.getConfiguration().setFlowNodeExecutionState(executionState);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
        .distributedByContains(DEFAULT_USERNAME, getCorrectTestExecutionValue(assignee2Count))
      .doAssert(result);
    // @formatter:on
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  static class ExecutionStateTestValues {
    Double expectedIdleDurationValue;
    Double expectedWorkDurationValue;
    Double expectedTotalDurationValue;
  }

  protected static Stream<Arguments> getExecutionStateExpectedValues() {
    return Stream.of(
      Arguments.of(
        FlowNodeExecutionState.RUNNING,
        new ExecutionStateTestValues(200., 500., 700.)
      ),
      Arguments.of(
        FlowNodeExecutionState.COMPLETED,
        new ExecutionStateTestValues(100., 100., 100.)
      ),
      Arguments.of(
        FlowNodeExecutionState.CANCELED,
        new ExecutionStateTestValues(100., 100., 100.)
      ),
      Arguments.of(
        FlowNodeExecutionState.ALL,
        new ExecutionStateTestValues(
          calculateExpectedValueGivenDurationsDefaultAggr(100., 200.),
          calculateExpectedValueGivenDurationsDefaultAggr(100., 500.),
          calculateExpectedValueGivenDurationsDefaultAggr(100., 700.)
        )
      )
    );
  }

  private String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }

  protected void changeUserTaskClaimDate(final ProcessInstanceEngineDto processInstanceDto,
                                         final OffsetDateTime now,
                                         final String userTaskKey,
                                         final long offsetDurationInMs) {

    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto ->
        {
          try {
            engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              now.minus(offsetDurationInMs, ChronoUnit.MILLIS)
            );
          } catch (SQLException e) {
            throw new OptimizeIntegrationTestException(e);
          }
        }
      );
  }

  protected abstract Double getCorrectTestExecutionValue(final ExecutionStateTestValues executionStateTestValues);

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_ASSIGNEE;
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
