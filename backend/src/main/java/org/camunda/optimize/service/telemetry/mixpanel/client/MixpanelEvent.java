/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry.mixpanel.client;

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

  public MixpanelEvent(final MixpanelEventName eventName, final MixpanelEventProperties properties) {
    this.event = EVENT_NAME_PREFIX + eventName;
    this.properties = properties;
  }
}
