/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.camunda.optimize.dto.optimize.ReportConstants.IDLE_USER_TASK_DURATION_TIME;
import static org.camunda.optimize.dto.optimize.ReportConstants.TOTAL_USER_TASK_DURATION_TIME;
import static org.camunda.optimize.dto.optimize.ReportConstants.WORK_USER_TASK_DURATION_TIME;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;

@AllArgsConstructor
@Getter
public enum UserTaskDurationTime {
  IDLE(IDLE_USER_TASK_DURATION_TIME, USER_TASK_IDLE_DURATION),
  WORK(WORK_USER_TASK_DURATION_TIME, USER_TASK_WORK_DURATION),
  TOTAL(TOTAL_USER_TASK_DURATION_TIME, FLOW_NODE_TOTAL_DURATION),
  ;

  private final String id;
  private final String durationFieldName;

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }
}
