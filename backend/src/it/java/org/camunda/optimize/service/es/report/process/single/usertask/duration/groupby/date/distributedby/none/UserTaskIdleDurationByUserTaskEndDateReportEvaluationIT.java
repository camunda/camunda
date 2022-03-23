/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.date.distributedby.none;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

public class UserTaskIdleDurationByUserTaskEndDateReportEvaluationIT
  extends UserTaskDurationByUserTaskEndDateReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.IDLE;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs) {
    changeUserTaskIdleDuration(processInstanceDto, durationInMs);
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String modelElementId,
                                final Double durationInMs) {
    changeUserTaskIdleDuration(processInstanceDto, modelElementId, durationInMs);
  }
}
