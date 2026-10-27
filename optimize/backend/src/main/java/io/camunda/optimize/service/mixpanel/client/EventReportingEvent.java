/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum EventReportingEvent {
  HEARTBEAT,

  ALERT_NEW_TRIGGERED,
  ALERT_REMINDER_TRIGGERED,
  ALERT_RESOLVED_TRIGGERED,

  REPORT_SHARE_ENABLED,
  REPORT_SHARE_DISABLED,
  DASHBOARD_SHARE_ENABLED,
  DASHBOARD_SHARE_DISABLED;

  @JsonValue
  public String getId() {
    return name().toLowerCase(Locale.ENGLISH);
  }

  @Override
  public String toString() {
    return getId();
  }
}
