/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.frequency.groupby.usertask.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.ModelElementFrequencyByModelElementDurationIT;
import org.camunda.optimize.test.util.ProcessReportDataType;

import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION;

public class UserTaskFrequencyByUserTaskDurationReportEvaluationIT extends ModelElementFrequencyByModelElementDurationIT {
  @Override
  protected void startProcessInstanceCompleteUserTaskAndModifyModelElementDuration(final String definitionId, final long durationInMillis) {
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(definitionId);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    engineDatabaseExtension.changeUserTaskDuration(processInstance.getId(), durationInMillis);
  }

  @Override
  protected ProcessViewEntity getModelElementView() {
    return ProcessViewEntity.USER_TASK;
  }

  @Override
  protected int getNumberOfModelElementsPerInstance() {
    return 1;
  }

  @Override
  protected ProcessReportDataType getReportDataType() {
    return USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION;
  }
}
