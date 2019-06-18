/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.groupby.assignee;

import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

import java.sql.SQLException;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskWorkDurationMapGroupByAssigneeReport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class UserTaskWorkDurationByAssigneeReportEvaluationIT
  extends AbstractUserTaskDurationByAssigneeReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.WORK;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(
        historicUserTaskInstanceDto ->
          changeUserOperationClaimTimestamp(
            processInstanceDto,
            setDuration,
            historicUserTaskInstanceDto
          )
      );
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final long duration) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto -> {
          if (historicUserTaskInstanceDto.getEndTime() != null) {
            changeUserOperationClaimTimestamp(
              processInstanceDto,
              duration,
              historicUserTaskInstanceDto
            );
          }
        }
      );
  }

  @Override
  protected ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return createUserTaskWorkDurationMapGroupByAssigneeReport(processDefinitionKey, version);
  }

  private void changeUserOperationClaimTimestamp(final ProcessInstanceEngineDto processInstanceDto,
                                                 final long millis,
                                                 final HistoricUserTaskInstanceDto historicUserTaskInstanceDto) {
    try {
      engineDatabaseRule.changeUserTaskClaimOperationTimestamp(
        processInstanceDto.getId(),
        historicUserTaskInstanceDto.getId(),
        historicUserTaskInstanceDto.getEndTime().minus(millis, ChronoUnit.MILLIS)
      );
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  @Override
  protected void assertEvaluateReportWithExecutionState(final ProcessDurationReportMapResultDto result,
                                                        final ExecutionStateTestValues expectedValues) {
    assertThat(
      result.getDataEntryForKey(DEFAULT_USERNAME).orElse(new MapResultEntryDto<>("foo", null)).getValue(),
      is(expectedValues.getExpectedWorkDurationValues().get(DEFAULT_USERNAME))
    );
    assertThat(
      result.getDataEntryForKey(SECOND_USER).orElse(new MapResultEntryDto<>("foo", null)).getValue(),
      is(expectedValues.getExpectedWorkDurationValues().get(SECOND_USER))
    );
  }

}
