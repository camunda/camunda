/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.date.distributed_by.candidate_group;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

import java.sql.SQLException;

public class UserTaskTotalDurationByUserTaskEndDateByAssgineeReportEvaluationIT
  extends UserTaskDurationByUserTaskEndDateByCandidateGroupReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.TOTAL;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final long duration) {
    try {
      engineDatabaseExtension.changeUserTaskDuration(processInstanceDto.getId(), userTaskKey, duration);
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration) {
    try {
      engineDatabaseExtension.changeUserTaskDuration(processInstanceDto.getId(), setDuration);
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }
}
