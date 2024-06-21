/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.alert;

import static io.camunda.optimize.dto.optimize.ReportConstants.ALERT_THRESHOLD_OPERATOR_GREATER;
import static io.camunda.optimize.dto.optimize.ReportConstants.ALERT_THRESHOLD_OPERATOR_LESS;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AlertThresholdOperator {
  GREATER(ALERT_THRESHOLD_OPERATOR_GREATER),
  LESS(ALERT_THRESHOLD_OPERATOR_LESS);

  private final String id;

  AlertThresholdOperator(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }
}
