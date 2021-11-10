/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.entities;

import java.util.Date;
import java.util.Objects;

public class MetricEntity extends TasklistEntity {
  private String event;
  private String value;
  private Date eventTime;

  public MetricEntity() {
    super();
  }

  public MetricEntity(String event, String value, Date eventTime) {
    this.event = event;
    this.value = value;
    this.eventTime = eventTime;
  }

  public String getEvent() {
    return event;
  }

  public void setEvent(String event) {
    this.event = event;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public Date getEventTime() {
    return eventTime;
  }

  public void setEventTime(Date eventTime) {
    this.eventTime = eventTime;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MetricEntity)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final MetricEntity that = (MetricEntity) o;
    return Objects.equals(event, that.event)
        && Objects.equals(value, that.value)
        && Objects.equals(eventTime, that.eventTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), event, value, eventTime);
  }
}
