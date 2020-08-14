/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.frequency.groupby.date.distributed_by.usertask;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Stream;

public class UserTaskFrequencyByUserTaskStartDateByUserTaskReportEvaluationIT extends UserTaskFrequencyByUserTaskDateByUserTaskReportEvaluationIT {

  @ParameterizedTest
  @MethodSource("getExecutionStateExpectedValues")
  public void evaluateReportWithExecutionState(ExecutionStateTestValues executionStateTestValues) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());

    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.DAY);
    reportData.getConfiguration().setFlowNodeExecutionState(executionStateTestValues.executionState);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
      .distributedByContains(USER_TASK_2, executionStateTestValues.expectedUserTask2Count, USER_TASK_2_NAME)
      .distributedByContains(USER_TASK_1, executionStateTestValues.expectedUserTask1Count, USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Data
  @AllArgsConstructor
  static class ExecutionStateTestValues {
    FlowNodeExecutionState executionState;
    Double expectedUserTask1Count;
    Double expectedUserTask2Count;
  }

  protected static Stream<ExecutionStateTestValues> getExecutionStateExpectedValues() {
    return Stream.of(
      new ExecutionStateTestValues(FlowNodeExecutionState.RUNNING, 1., null),
      new ExecutionStateTestValues(FlowNodeExecutionState.COMPLETED, 1., 1.),
      new ExecutionStateTestValues(FlowNodeExecutionState.ALL, 2., 1.)
    );
  }


  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_USER_TASK;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected void changeModelElementDates(final Map<String, OffsetDateTime> updates) {
    engineDatabaseExtension.changeUserTaskStartDates(updates);
  }

  @Override
  protected void changeModelElementDate(final ProcessInstanceEngineDto processInstance,
                                        final String userTaskKey,
                                        final OffsetDateTime dateToChangeTo) {
    engineDatabaseExtension.changeUserTaskStartDate(processInstance.getId(), userTaskKey, dateToChangeTo);
  }
}
