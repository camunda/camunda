/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.IDLE_USER_TASK_DURATION_TIME;
import static org.camunda.optimize.dto.optimize.ReportConstants.TOTAL_USER_TASK_DURATION_TIME;
import static org.camunda.optimize.dto.optimize.ReportConstants.WORK_USER_TASK_DURATION_TIME;

public enum UserTaskDurationTime {
  IDLE(IDLE_USER_TASK_DURATION_TIME),
  WORK(WORK_USER_TASK_DURATION_TIME),
  TOTAL(TOTAL_USER_TASK_DURATION_TIME),
  ;

  private final String id;

  UserTaskDurationTime(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }
}
