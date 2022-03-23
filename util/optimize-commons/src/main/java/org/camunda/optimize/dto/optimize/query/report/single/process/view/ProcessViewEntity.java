/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.view;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_FLOW_NODE_ENTITY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_INCIDENT_ENTITY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_USER_TASK_ENTITY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_VARIABLE_ENTITY;

public enum ProcessViewEntity {
  FLOW_NODE(VIEW_FLOW_NODE_ENTITY),
  USER_TASK(VIEW_USER_TASK_ENTITY),
  PROCESS_INSTANCE(VIEW_PROCESS_INSTANCE_ENTITY),
  VARIABLE(VIEW_VARIABLE_ENTITY),
  INCIDENT(VIEW_INCIDENT_ENTITY);

  private final String id;

  ProcessViewEntity(final String id) {
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
