/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.alert;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum AlertNotificationType {
  NEW("alert_new_triggered"),
  REMINDER("alert_reminder"),
  RESOLVED("alert_resolved"),
  ;

  @JsonIgnore private final String utmSource;

  private AlertNotificationType(final String utmSource) {
    this.utmSource = utmSource;
  }

  @JsonValue
  public String getId() {
    return name().toLowerCase(Locale.ENGLISH);
  }

  public String getUtmSource() {
    return utmSource;
  }
}
