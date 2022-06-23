/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.date.distributedby.flownode;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.ProcessReportDataType;

import java.time.OffsetDateTime;
import java.util.Map;

public class FlowNodeFrequencyByFlowNodeStartDateByFlowNodeReportEvaluationIT
  extends FlowNodeFrequencyByFlowNodeDateByFlowNodeReportEvaluationIT {

  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_START_DATE_BY_FLOW_NODE;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected void changeModelElementDates(final Map<String, OffsetDateTime> updates) {
    engineDatabaseExtension.changeAllFlowNodeStartDates(updates);
  }

  @Override
  protected void changeModelElementDate(final ProcessInstanceEngineDto processInstance,
                                        final String ActivityInstanceId,
                                        final OffsetDateTime dateToChangeTo) {
    engineDatabaseExtension.changeFlowNodeStartDate(
      processInstance.getId(),
      ActivityInstanceId,
      dateToChangeTo
    );
  }
}
