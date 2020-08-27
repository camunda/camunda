/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;

import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_DURATION;

public class CombinedProcessInstanceFrequencyByDurationReportIT extends AbstractCombinedDurationReportIT {

  @Override
  protected void startInstanceAndModifyRelevantDurations(final String definitionId, final int durationInMillis) {
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(definitionId);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    changeProcessInstanceDuration(processInstance, durationInMillis);
  }

  @Override
  protected ProcessReportDataType getReportDataType() {
    return COUNT_PROC_INST_FREQ_GROUP_BY_DURATION;
  }

}
