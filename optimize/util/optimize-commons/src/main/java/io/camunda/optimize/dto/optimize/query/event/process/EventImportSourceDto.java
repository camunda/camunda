/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceConfigDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import java.time.OffsetDateTime;
import java.util.List;

public class EventImportSourceDto {

  private OffsetDateTime firstEventForSourceAtTimeOfPublishTimestamp;
  private OffsetDateTime lastEventForSourceAtTimeOfPublishTimestamp;

  private OffsetDateTime lastImportedEventTimestamp;
  private OffsetDateTime lastImportExecutionTimestamp;

  private EventSourceType eventImportSourceType;
  // If the source type is 'Camunda', there should be a single config in this list. If 'External',
  // there can be multiple
  private List<EventSourceConfigDto> eventSourceConfigurations;

  public EventImportSourceDto(
      final OffsetDateTime firstEventForSourceAtTimeOfPublishTimestamp,
      final OffsetDateTime lastEventForSourceAtTimeOfPublishTimestamp,
      final OffsetDateTime lastImportedEventTimestamp,
      final OffsetDateTime lastImportExecutionTimestamp,
      final EventSourceType eventImportSourceType,
      final List<EventSourceConfigDto> eventSourceConfigurations) {
    this.firstEventForSourceAtTimeOfPublishTimestamp = firstEventForSourceAtTimeOfPublishTimestamp;
    this.lastEventForSourceAtTimeOfPublishTimestamp = lastEventForSourceAtTimeOfPublishTimestamp;
    this.lastImportedEventTimestamp = lastImportedEventTimestamp;
    this.lastImportExecutionTimestamp = lastImportExecutionTimestamp;
    this.eventImportSourceType = eventImportSourceType;
    this.eventSourceConfigurations = eventSourceConfigurations;
  }

  public EventImportSourceDto() {}

  public OffsetDateTime getFirstEventForSourceAtTimeOfPublishTimestamp() {
    return firstEventForSourceAtTimeOfPublishTimestamp;
  }

  public void setFirstEventForSourceAtTimeOfPublishTimestamp(
      final OffsetDateTime firstEventForSourceAtTimeOfPublishTimestamp) {
    this.firstEventForSourceAtTimeOfPublishTimestamp = firstEventForSourceAtTimeOfPublishTimestamp;
  }

  public OffsetDateTime getLastEventForSourceAtTimeOfPublishTimestamp() {
    return lastEventForSourceAtTimeOfPublishTimestamp;
  }

  public void setLastEventForSourceAtTimeOfPublishTimestamp(
      final OffsetDateTime lastEventForSourceAtTimeOfPublishTimestamp) {
    this.lastEventForSourceAtTimeOfPublishTimestamp = lastEventForSourceAtTimeOfPublishTimestamp;
  }

  public OffsetDateTime getLastImportedEventTimestamp() {
    return lastImportedEventTimestamp;
  }

  public void setLastImportedEventTimestamp(final OffsetDateTime lastImportedEventTimestamp) {
    this.lastImportedEventTimestamp = lastImportedEventTimestamp;
  }

  public OffsetDateTime getLastImportExecutionTimestamp() {
    return lastImportExecutionTimestamp;
  }

  public void setLastImportExecutionTimestamp(final OffsetDateTime lastImportExecutionTimestamp) {
    this.lastImportExecutionTimestamp = lastImportExecutionTimestamp;
  }

  public EventSourceType getEventImportSourceType() {
    return eventImportSourceType;
  }

  public void setEventImportSourceType(final EventSourceType eventImportSourceType) {
    this.eventImportSourceType = eventImportSourceType;
  }

  public List<EventSourceConfigDto> getEventSourceConfigurations() {
    return eventSourceConfigurations;
  }

  public void setEventSourceConfigurations(
      final List<EventSourceConfigDto> eventSourceConfigurations) {
    this.eventSourceConfigurations = eventSourceConfigurations;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventImportSourceDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $firstEventForSourceAtTimeOfPublishTimestamp =
        getFirstEventForSourceAtTimeOfPublishTimestamp();
    result =
        result * PRIME
            + ($firstEventForSourceAtTimeOfPublishTimestamp == null
                ? 43
                : $firstEventForSourceAtTimeOfPublishTimestamp.hashCode());
    final Object $lastEventForSourceAtTimeOfPublishTimestamp =
        getLastEventForSourceAtTimeOfPublishTimestamp();
    result =
        result * PRIME
            + ($lastEventForSourceAtTimeOfPublishTimestamp == null
                ? 43
                : $lastEventForSourceAtTimeOfPublishTimestamp.hashCode());
    final Object $lastImportedEventTimestamp = getLastImportedEventTimestamp();
    result =
        result * PRIME
            + ($lastImportedEventTimestamp == null ? 43 : $lastImportedEventTimestamp.hashCode());
    final Object $lastImportExecutionTimestamp = getLastImportExecutionTimestamp();
    result =
        result * PRIME
            + ($lastImportExecutionTimestamp == null
                ? 43
                : $lastImportExecutionTimestamp.hashCode());
    final Object $eventImportSourceType = getEventImportSourceType();
    result =
        result * PRIME + ($eventImportSourceType == null ? 43 : $eventImportSourceType.hashCode());
    final Object $eventSourceConfigurations = getEventSourceConfigurations();
    result =
        result * PRIME
            + ($eventSourceConfigurations == null ? 43 : $eventSourceConfigurations.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventImportSourceDto)) {
      return false;
    }
    final EventImportSourceDto other = (EventImportSourceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$firstEventForSourceAtTimeOfPublishTimestamp =
        getFirstEventForSourceAtTimeOfPublishTimestamp();
    final Object other$firstEventForSourceAtTimeOfPublishTimestamp =
        other.getFirstEventForSourceAtTimeOfPublishTimestamp();
    if (this$firstEventForSourceAtTimeOfPublishTimestamp == null
        ? other$firstEventForSourceAtTimeOfPublishTimestamp != null
        : !this$firstEventForSourceAtTimeOfPublishTimestamp.equals(
            other$firstEventForSourceAtTimeOfPublishTimestamp)) {
      return false;
    }
    final Object this$lastEventForSourceAtTimeOfPublishTimestamp =
        getLastEventForSourceAtTimeOfPublishTimestamp();
    final Object other$lastEventForSourceAtTimeOfPublishTimestamp =
        other.getLastEventForSourceAtTimeOfPublishTimestamp();
    if (this$lastEventForSourceAtTimeOfPublishTimestamp == null
        ? other$lastEventForSourceAtTimeOfPublishTimestamp != null
        : !this$lastEventForSourceAtTimeOfPublishTimestamp.equals(
            other$lastEventForSourceAtTimeOfPublishTimestamp)) {
      return false;
    }
    final Object this$lastImportedEventTimestamp = getLastImportedEventTimestamp();
    final Object other$lastImportedEventTimestamp = other.getLastImportedEventTimestamp();
    if (this$lastImportedEventTimestamp == null
        ? other$lastImportedEventTimestamp != null
        : !this$lastImportedEventTimestamp.equals(other$lastImportedEventTimestamp)) {
      return false;
    }
    final Object this$lastImportExecutionTimestamp = getLastImportExecutionTimestamp();
    final Object other$lastImportExecutionTimestamp = other.getLastImportExecutionTimestamp();
    if (this$lastImportExecutionTimestamp == null
        ? other$lastImportExecutionTimestamp != null
        : !this$lastImportExecutionTimestamp.equals(other$lastImportExecutionTimestamp)) {
      return false;
    }
    final Object this$eventImportSourceType = getEventImportSourceType();
    final Object other$eventImportSourceType = other.getEventImportSourceType();
    if (this$eventImportSourceType == null
        ? other$eventImportSourceType != null
        : !this$eventImportSourceType.equals(other$eventImportSourceType)) {
      return false;
    }
    final Object this$eventSourceConfigurations = getEventSourceConfigurations();
    final Object other$eventSourceConfigurations = other.getEventSourceConfigurations();
    if (this$eventSourceConfigurations == null
        ? other$eventSourceConfigurations != null
        : !this$eventSourceConfigurations.equals(other$eventSourceConfigurations)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventImportSourceDto(firstEventForSourceAtTimeOfPublishTimestamp="
        + getFirstEventForSourceAtTimeOfPublishTimestamp()
        + ", lastEventForSourceAtTimeOfPublishTimestamp="
        + getLastEventForSourceAtTimeOfPublishTimestamp()
        + ", lastImportedEventTimestamp="
        + getLastImportedEventTimestamp()
        + ", lastImportExecutionTimestamp="
        + getLastImportExecutionTimestamp()
        + ", eventImportSourceType="
        + getEventImportSourceType()
        + ", eventSourceConfigurations="
        + getEventSourceConfigurations()
        + ")";
  }

  public static EventImportSourceDtoBuilder builder() {
    return new EventImportSourceDtoBuilder();
  }

  public static final class Fields {

    public static final String firstEventForSourceAtTimeOfPublishTimestamp =
        "firstEventForSourceAtTimeOfPublishTimestamp";
    public static final String lastEventForSourceAtTimeOfPublishTimestamp =
        "lastEventForSourceAtTimeOfPublishTimestamp";
    public static final String lastImportedEventTimestamp = "lastImportedEventTimestamp";
    public static final String lastImportExecutionTimestamp = "lastImportExecutionTimestamp";
    public static final String eventImportSourceType = "eventImportSourceType";
    public static final String eventSourceConfigurations = "eventSourceConfigurations";
  }

  public static class EventImportSourceDtoBuilder {

    private OffsetDateTime firstEventForSourceAtTimeOfPublishTimestamp;
    private OffsetDateTime lastEventForSourceAtTimeOfPublishTimestamp;
    private OffsetDateTime lastImportedEventTimestamp;
    private OffsetDateTime lastImportExecutionTimestamp;
    private EventSourceType eventImportSourceType;
    private List<EventSourceConfigDto> eventSourceConfigurations;

    EventImportSourceDtoBuilder() {}

    public EventImportSourceDtoBuilder firstEventForSourceAtTimeOfPublishTimestamp(
        final OffsetDateTime firstEventForSourceAtTimeOfPublishTimestamp) {
      this.firstEventForSourceAtTimeOfPublishTimestamp =
          firstEventForSourceAtTimeOfPublishTimestamp;
      return this;
    }

    public EventImportSourceDtoBuilder lastEventForSourceAtTimeOfPublishTimestamp(
        final OffsetDateTime lastEventForSourceAtTimeOfPublishTimestamp) {
      this.lastEventForSourceAtTimeOfPublishTimestamp = lastEventForSourceAtTimeOfPublishTimestamp;
      return this;
    }

    public EventImportSourceDtoBuilder lastImportedEventTimestamp(
        final OffsetDateTime lastImportedEventTimestamp) {
      this.lastImportedEventTimestamp = lastImportedEventTimestamp;
      return this;
    }

    public EventImportSourceDtoBuilder lastImportExecutionTimestamp(
        final OffsetDateTime lastImportExecutionTimestamp) {
      this.lastImportExecutionTimestamp = lastImportExecutionTimestamp;
      return this;
    }

    public EventImportSourceDtoBuilder eventImportSourceType(
        final EventSourceType eventImportSourceType) {
      this.eventImportSourceType = eventImportSourceType;
      return this;
    }

    public EventImportSourceDtoBuilder eventSourceConfigurations(
        final List<EventSourceConfigDto> eventSourceConfigurations) {
      this.eventSourceConfigurations = eventSourceConfigurations;
      return this;
    }

    public EventImportSourceDto build() {
      return new EventImportSourceDto(
          firstEventForSourceAtTimeOfPublishTimestamp,
          lastEventForSourceAtTimeOfPublishTimestamp,
          lastImportedEventTimestamp,
          lastImportExecutionTimestamp,
          eventImportSourceType,
          eventSourceConfigurations);
    }

    @Override
    public String toString() {
      return "EventImportSourceDto.EventImportSourceDtoBuilder(firstEventForSourceAtTimeOfPublishTimestamp="
          + firstEventForSourceAtTimeOfPublishTimestamp
          + ", lastEventForSourceAtTimeOfPublishTimestamp="
          + lastEventForSourceAtTimeOfPublishTimestamp
          + ", lastImportedEventTimestamp="
          + lastImportedEventTimestamp
          + ", lastImportExecutionTimestamp="
          + lastImportExecutionTimestamp
          + ", eventImportSourceType="
          + eventImportSourceType
          + ", eventSourceConfigurations="
          + eventSourceConfigurations
          + ")";
    }
  }
}
