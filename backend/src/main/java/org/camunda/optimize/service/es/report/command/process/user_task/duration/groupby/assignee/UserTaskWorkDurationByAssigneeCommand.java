/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.user_task.duration.groupby.assignee;

import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;

public class UserTaskWorkDurationByAssigneeCommand extends AbstractUserTaskDurationByAssigneeCommand {


  public UserTaskWorkDurationByAssigneeCommand(final AggregationStrategy strategy) {
    super(strategy);
  }

  @Override
  protected String getDurationFieldName() {
    return ProcessInstanceType.USER_TASK_WORK_DURATION;
  }


  @Override
  protected String getReferenceDateFieldName() {
    return ProcessInstanceType.USER_TASK_CLAIM_DATE;
  }
}
