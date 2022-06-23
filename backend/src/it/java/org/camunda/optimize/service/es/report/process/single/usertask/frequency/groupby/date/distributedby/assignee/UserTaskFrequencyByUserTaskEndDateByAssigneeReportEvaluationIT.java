/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.frequency.groupby.date.distributedby.assignee;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.ProcessReportDataType;

import java.time.OffsetDateTime;
import java.util.Map;

public class UserTaskFrequencyByUserTaskEndDateByAssigneeReportEvaluationIT
  extends UserTaskFrequencyByUserTaskDateByAssigneeReportEvaluationIT {


  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_END_DATE_BY_ASSIGNEE;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.END_DATE;
  }

  @Override
  protected void changeUserTaskDates(final Map<String, OffsetDateTime> updates) {
    engineDatabaseExtension.changeAllFlowNodeEndDates(updates);
  }

  @Override
  protected void changeUserTaskDate(final ProcessInstanceEngineDto processInstance,
                                    final String userTaskKey,
                                    final OffsetDateTime dateToChangeTo) {
    engineDatabaseExtension.changeFlowNodeEndDate(processInstance.getId(), userTaskKey, dateToChangeTo);
    engineDatabaseExtension.changeFlowNodeEndDate(processInstance.getId(), userTaskKey, dateToChangeTo);
  }
}
