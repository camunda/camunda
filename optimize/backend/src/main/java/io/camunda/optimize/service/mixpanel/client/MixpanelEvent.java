/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

import java.util.Objects;

public class MixpanelEvent {

  public static final String EVENT_NAME_PREFIX = "optimize:";

  private String event;
  private MixpanelEventProperties properties;

  public MixpanelEvent(
      final EventReportingEvent eventName, final MixpanelEventProperties properties) {
    event = EVENT_NAME_PREFIX + eventName;
    this.properties = properties;
  }

  public MixpanelEvent(final String event, final MixpanelEventProperties properties) {
    this.event = event;
    this.properties = properties;
  }

  protected MixpanelEvent() {}

  public String getEvent() {
    return event;
  }

  public void setEvent(final String event) {
    this.event = event;
  }

  public MixpanelEventProperties getProperties() {
    return properties;
  }

  public void setProperties(final MixpanelEventProperties properties) {
    this.properties = properties;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MixpanelEvent;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MixpanelEvent that = (MixpanelEvent) o;
    return Objects.equals(event, that.event) && Objects.equals(properties, that.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(event, properties);
  }

  @Override
  public String toString() {
    return "MixpanelEvent(event=" + getEvent() + ", properties=" + getProperties() + ")";
  }
}
