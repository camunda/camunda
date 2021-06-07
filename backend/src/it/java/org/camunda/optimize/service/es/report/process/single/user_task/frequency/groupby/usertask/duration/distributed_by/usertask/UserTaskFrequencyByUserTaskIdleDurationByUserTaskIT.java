/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.frequency.groupby.usertask.duration.distributed_by.usertask;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

import java.time.OffsetDateTime;

public class UserTaskFrequencyByUserTaskIdleDurationByUserTaskIT
  extends AbstractUserTaskFrequencyByUserTaskDurationByUserTaskIT {

  @Override
  protected ProcessInstanceEngineDto startProcessInstanceCompleteTaskAndModifyDuration(
    final String definitionId,
    final Number durationInMillis) {
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(definitionId);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    changeUserTaskIdleDuration(processInstance, durationInMillis);
    return processInstance;
  }

  @Override
  protected void changeRunningInstanceReferenceDate(final ProcessInstanceEngineDto runningProcessInstance,
                                                    final OffsetDateTime startTime) {
    engineDatabaseExtension.changeFlowNodeStartDate(runningProcessInstance.getId(), USER_TASK_1, startTime);
  }

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.IDLE;
  }

}
