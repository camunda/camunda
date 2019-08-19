/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.user_task.duration.groupby.assignee.distributed_by.none;

import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;

public class UserTaskIdleDurationByAssigneeCommand extends AbstractUserTaskDurationByAssigneeCommand {


  public UserTaskIdleDurationByAssigneeCommand(final AggregationStrategy strategy) {
    super(strategy);
  }

  @Override
  protected String getDurationFieldName() {
    return ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
  }

  @Override
  protected String getReferenceDateFieldName() {
    return ProcessInstanceIndex.USER_TASK_START_DATE;
  }
}
