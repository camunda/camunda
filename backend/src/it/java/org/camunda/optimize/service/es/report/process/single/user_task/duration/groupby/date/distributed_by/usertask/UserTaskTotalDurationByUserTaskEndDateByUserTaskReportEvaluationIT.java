/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.date.distributed_by.usertask;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

public class UserTaskTotalDurationByUserTaskEndDateByUserTaskReportEvaluationIT
  extends UserTaskDurationByUserTaskEndDateByUserTaskReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.TOTAL;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final Double duration) {
    changeUserTaskTotalDuration(processInstanceDto, userTaskKey, duration);
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Double duration) {
    changeUserTaskTotalDuration(processInstanceDto, duration);
  }
}
