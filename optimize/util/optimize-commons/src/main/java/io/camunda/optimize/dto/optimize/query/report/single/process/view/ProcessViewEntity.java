/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.view;

import static io.camunda.optimize.dto.optimize.ReportConstants.VIEW_FLOW_NODE_ENTITY;
import static io.camunda.optimize.dto.optimize.ReportConstants.VIEW_INCIDENT_ENTITY;
import static io.camunda.optimize.dto.optimize.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static io.camunda.optimize.dto.optimize.ReportConstants.VIEW_USER_TASK_ENTITY;
import static io.camunda.optimize.dto.optimize.ReportConstants.VIEW_VARIABLE_ENTITY;

import com.fasterxml.jackson.annotation.JsonValue;

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
