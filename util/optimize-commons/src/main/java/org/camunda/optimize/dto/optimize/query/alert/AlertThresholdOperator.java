/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.alert;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALERT_THRESHOLD_OPERATOR_GREATER;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALERT_THRESHOLD_OPERATOR_LESS;

public enum AlertThresholdOperator {
  GREATER(ALERT_THRESHOLD_OPERATOR_GREATER),
  LESS(ALERT_THRESHOLD_OPERATOR_LESS),
  ;

  private final String id;

  AlertThresholdOperator(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }
}