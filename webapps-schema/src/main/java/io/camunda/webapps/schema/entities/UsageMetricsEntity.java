/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import java.time.OffsetDateTime;
import java.util.Objects;

public class UsageMetricsEntity {

  private String id;
  private OffsetDateTime eventTime;
  private String event;
  private String value;

  @Override
  public int hashCode() {
    return Objects.hash(id, eventTime, event, value);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UsageMetricsEntity that = (UsageMetricsEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(eventTime, that.eventTime)
        && Objects.equals(event, that.event)
        && Objects.equals(value, that.value);
  }

  @Override
  public String toString() {
    return "UsageMetricsEntity{"
        + "id='"
        + id
        + '\''
        + ", eventTime="
        + eventTime
        + ", event='"
        + event
        + '\''
        + ", value='"
        + value
        + '\''
        + '}';
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public OffsetDateTime getEventTime() {
    return eventTime;
  }

  public void setEventTime(final OffsetDateTime eventTime) {
    this.eventTime = eventTime;
  }

  public String getEvent() {
    return event;
  }

  public void setEvent(final String event) {
    this.event = event;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }
}
