/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MixpanelEvent {
  public static final String EVENT_NAME_PREFIX = "optimize:";

  private String event;
  private MixpanelEventProperties properties;

  public MixpanelEvent(
      final EventReportingEvent eventName, final MixpanelEventProperties properties) {
    this.event = EVENT_NAME_PREFIX + eventName;
    this.properties = properties;
  }
}
