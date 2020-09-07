/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.camunda.optimize.dto.optimize.ReportConstants.IDLE_USER_TASK_DURATION_TIME;
import static org.camunda.optimize.dto.optimize.ReportConstants.TOTAL_USER_TASK_DURATION_TIME;
import static org.camunda.optimize.dto.optimize.ReportConstants.WORK_USER_TASK_DURATION_TIME;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CLAIM_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;

@AllArgsConstructor
@Getter
public enum UserTaskDurationTime {
  IDLE(IDLE_USER_TASK_DURATION_TIME, USER_TASK_IDLE_DURATION, USER_TASK_START_DATE),
  WORK(WORK_USER_TASK_DURATION_TIME, USER_TASK_WORK_DURATION, USER_TASK_CLAIM_DATE),
  TOTAL(TOTAL_USER_TASK_DURATION_TIME, USER_TASK_TOTAL_DURATION, USER_TASK_START_DATE),
  ;

  private final String id;
  private final String durationFieldName;
  private final String startDateFieldName;

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }
}
