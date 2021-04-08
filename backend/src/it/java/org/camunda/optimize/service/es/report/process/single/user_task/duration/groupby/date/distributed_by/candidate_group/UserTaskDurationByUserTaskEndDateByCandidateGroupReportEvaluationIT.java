/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.date.distributed_by.candidate_group;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.test.util.ProcessReportDataType;

public abstract class UserTaskDurationByUserTaskEndDateByCandidateGroupReportEvaluationIT
  extends UserTaskDurationByUserTaskDateByCandidateGroupReportEvaluationIT {

  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_CANDIDATE_GROUP;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.END_DATE;
  }

}
