/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.view;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_FREQUENCY_PROPERTY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_RAW_DATA_OPERATION;

public enum DecisionViewProperty {
  FREQUENCY(VIEW_FREQUENCY_PROPERTY),
  RAW_DATA(VIEW_RAW_DATA_OPERATION)
  ;

  private final String id;

  DecisionViewProperty(final String id) {
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
