/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.frequency.groupby.duration.distributedby.none;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.ModelElementFrequencyByModelElementDurationIT;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.time.OffsetDateTime;

import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION;

public class UserTaskFrequencyByUserTaskIdleDurationReportEvaluationIT
  extends ModelElementFrequencyByModelElementDurationIT {

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
  protected ProcessViewEntity getModelElementView() {
    return ProcessViewEntity.USER_TASK;
  }

  @Override
  protected int getNumberOfModelElementsPerInstance() {
    return 1;
  }

  @Override
  protected ProcessReportDataDto createReport(final String processKey, final String definitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION)
      .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
      .build();
  }
}
