/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_FLOWNODE_EXECUTION_STATE;
import static org.camunda.optimize.dto.optimize.ReportConstants.CANCELED_FLOWNODE_EXECUTION_STATE;
import static org.camunda.optimize.dto.optimize.ReportConstants.COMPLETED_FLOWNODE_EXECUTION_STATE;
import static org.camunda.optimize.dto.optimize.ReportConstants.RUNNING_FLOWNODE_EXECUTION_STATE;

public enum FlowNodeExecutionState {
  RUNNING(RUNNING_FLOWNODE_EXECUTION_STATE),
  COMPLETED(COMPLETED_FLOWNODE_EXECUTION_STATE),
  CANCELED(CANCELED_FLOWNODE_EXECUTION_STATE),
  ALL(ALL_FLOWNODE_EXECUTION_STATE),
  ;

  private final String id;

  FlowNodeExecutionState(final String id) {
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
