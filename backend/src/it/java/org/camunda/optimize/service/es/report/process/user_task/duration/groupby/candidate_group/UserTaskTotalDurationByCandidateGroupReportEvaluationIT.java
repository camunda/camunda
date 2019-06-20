/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.user_task.duration.groupby.candidate_group;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

import java.sql.SQLException;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createUserTaskTotalDurationMapGroupByCandidateGroupReport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class UserTaskTotalDurationByCandidateGroupReportEvaluationIT
  extends AbstractUserTaskDurationByCandidateGroupReportEvaluationIT {

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
    return createUserTaskTotalDurationMapGroupByCandidateGroupReport(processDefinitionKey, version);
  }

  @Override
  protected void assertEvaluateReportWithExecutionState(final ProcessDurationReportMapResultDto result,
                                                        final ExecutionStateTestValues expectedValues) {
    assertThat(
      result.getDataEntryForKey(FIRST_CANDIDATE_GROUP).map(MapResultEntryDto::getValue).orElse(null),
      is(expectedValues.getExpectedTotalDurationValues().get(FIRST_CANDIDATE_GROUP))
    );
    assertThat(
      result.getDataEntryForKey(SECOND_CANDIDATE_GROUP).map(MapResultEntryDto::getValue).orElse(null),
      is(expectedValues.getExpectedTotalDurationValues().get(SECOND_CANDIDATE_GROUP))
    );
  }

}
