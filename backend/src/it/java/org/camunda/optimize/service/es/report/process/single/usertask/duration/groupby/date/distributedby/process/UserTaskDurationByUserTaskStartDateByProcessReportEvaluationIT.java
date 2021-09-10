/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.duration.groupby.date.distributedby.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.service.es.report.process.single.flownode.duration.groupby.date.distributedby.process.FlowNodeDurationByFlowNodeDateByProcessReportEvaluationIT;
import org.camunda.optimize.test.util.ProcessReportDataType;

public class UserTaskDurationByUserTaskStartDateByProcessReportEvaluationIT
  extends UserTaskDurationByUserTaskDateByProcessReportEvaluationIT {

  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_DUR_GROUP_BY_USER_TASK_START_DATE_BY_PROCESS;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }
}

