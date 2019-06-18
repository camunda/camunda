/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.groupby.usertask;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

import java.sql.SQLException;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskTotalDurationMapGroupByUserTaskReport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class UserTaskTotalDurationByUserTaskReportEvaluationIT
  extends AbstractUserTaskDurationByUserTaskReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.TOTAL;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final long duration) {
    try {
      engineDatabaseRule.changeUserTaskDuration(processInstanceDto.getId(), userTaskKey, duration);
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration) {
    try {
      engineDatabaseRule.changeUserTaskDuration(processInstanceDto.getId(), setDuration);
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  @Override
  protected ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return createUserTaskTotalDurationMapGroupByUserTaskReport(processDefinitionKey, version);
  }

  @Override
  protected void assertEvaluateReportWithExecutionState(final ProcessDurationReportMapResultDto result,
                                                        final ExecutionStateTestValues expectedValues) {
    assertThat(
      result.getDataEntryForKey(USER_TASK_1).get().getValue(),
      is(expectedValues.getExpectedTotalDurationValues().get(USER_TASK_1))
    );
    assertThat(
      result.getDataEntryForKey(USER_TASK_2).get().getValue(),
      is(expectedValues.getExpectedTotalDurationValues().get(USER_TASK_2))
    );
  }

}
