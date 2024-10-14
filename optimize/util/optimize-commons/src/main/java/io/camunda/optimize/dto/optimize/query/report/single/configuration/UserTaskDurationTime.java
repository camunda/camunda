/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration;

import static io.camunda.optimize.dto.optimize.ReportConstants.IDLE_USER_TASK_DURATION_TIME;
import static io.camunda.optimize.dto.optimize.ReportConstants.TOTAL_USER_TASK_DURATION_TIME;
import static io.camunda.optimize.dto.optimize.ReportConstants.WORK_USER_TASK_DURATION_TIME;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UserTaskDurationTime {
  IDLE(IDLE_USER_TASK_DURATION_TIME, USER_TASK_IDLE_DURATION),
  WORK(WORK_USER_TASK_DURATION_TIME, USER_TASK_WORK_DURATION),
  TOTAL(TOTAL_USER_TASK_DURATION_TIME, FLOW_NODE_TOTAL_DURATION),
  ;

  private final String id;
  private final String durationFieldName;

  private UserTaskDurationTime(final String id, final String durationFieldName) {
    this.id = id;
    this.durationFieldName = durationFieldName;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }

  public String getDurationFieldName() {
    return durationFieldName;
  }
}
