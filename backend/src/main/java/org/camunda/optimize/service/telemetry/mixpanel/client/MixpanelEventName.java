/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry.mixpanel.client;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MixpanelEventName {
  HEARTBEAT,
  ALERT_NEW_TRIGGERED,
  ALERT_REMINDER_TRIGGERED,
  ALERT_RESOLVED_TRIGGERED,
  ;

  @JsonValue
  public String getId() {
    return name().toLowerCase();
  }

  @Override
  public String toString() {
    return getId();
  }
}
