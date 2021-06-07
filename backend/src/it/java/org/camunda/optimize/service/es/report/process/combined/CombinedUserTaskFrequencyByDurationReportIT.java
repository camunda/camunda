/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;

import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION;

public class CombinedUserTaskFrequencyByDurationReportIT extends AbstractCombinedDurationReportIT {

  @Override
  protected void startInstanceAndModifyRelevantDurations(final String definitionId, final int durationInMillis) {
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(definitionId);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(processInstance.getId(), durationInMillis);
  }

  @Override
  protected ProcessReportDataType getReportDataType() {
    return USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION;
  }

}
