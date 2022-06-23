/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.ProcessReportDataType;

import static org.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_DURATION;

public class CombinedProcessInstanceFrequencyByDurationReportIT extends AbstractCombinedDurationReportIT {

  @Override
  protected void startInstanceAndModifyRelevantDurations(final String definitionId, final int durationInMillis) {
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(definitionId);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    changeProcessInstanceDuration(processInstance, durationInMillis);
  }

  @Override
  protected ProcessReportDataType getReportDataType() {
    return PROC_INST_FREQ_GROUP_BY_DURATION;
  }

}
