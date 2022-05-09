/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.alert;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AlertNotificationType {
  NEW("alert_new_triggered"),
  REMINDER("alert_reminder"),
  RESOLVED("alert_resolved"),
  ;

  @JsonIgnore
  private final String utmSource;

  @JsonValue
  public String getId() {
    return name().toLowerCase();
  }
}
