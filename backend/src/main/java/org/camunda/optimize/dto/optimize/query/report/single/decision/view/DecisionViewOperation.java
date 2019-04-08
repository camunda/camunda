/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.view;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_COUNT_OPERATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_RAW_DATA_OPERATION;

public enum DecisionViewOperation {
  RAW(VIEW_RAW_DATA_OPERATION),
  COUNT(VIEW_COUNT_OPERATION),
  ;

  private final String id;

  DecisionViewOperation(final String id) {
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
