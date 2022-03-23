/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.date.distributedby.assignee;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

public class UserTaskWorkDurationByUserTaskStartDateByAssigneeReportEvaluationIT
  extends UserTaskDurationByUserTaskStartDateByAssigneeReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.WORK;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs) {
    changeUserTaskWorkDuration(processInstanceDto, durationInMs);
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final Double durationInMs) {
    changeUserTaskWorkDuration(processInstanceDto, userTaskKey, durationInMs);
  }

  @Override
  protected Double getCorrectTestExecutionValue(final FlowNodeStatusTestValues flowNodeStatusTestValues) {
    return flowNodeStatusTestValues.expectedWorkDurationValue;
  }
}
