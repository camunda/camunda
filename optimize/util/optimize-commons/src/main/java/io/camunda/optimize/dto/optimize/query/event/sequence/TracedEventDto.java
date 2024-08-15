/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.sequence;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventDto;

public class TracedEventDto implements OptimizeDto {

  private String eventId;
  private String group;
  private String source;
  private String eventName;
  private Long timestamp;
  private Long orderCounter;

  public TracedEventDto(
      final String eventId,
      final String group,
      final String source,
      final String eventName,
      final Long timestamp,
      final Long orderCounter) {
    this.eventId = eventId;
    this.group = group;
    this.source = source;
    this.eventName = eventName;
    this.timestamp = timestamp;
    this.orderCounter = orderCounter;
  }

  public TracedEventDto() {}

  public static TracedEventDto fromEventDto(final EventDto eventDto) {
    return TracedEventDto.builder()
        .eventId(eventDto.getId())
        .timestamp(eventDto.getTimestamp())
        .group(eventDto.getGroup())
        .source(eventDto.getSource())
        .eventName(eventDto.getEventName())
        .orderCounter(
            eventDto instanceof OrderedEventDto
                ? ((OrderedEventDto) eventDto).getOrderCounter()
                : null)
        .build();
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(final String eventId) {
    this.eventId = eventId;
  }

  public String getGroup() {
    return group;
  }

  public void setGroup(final String group) {
    this.group = group;
  }

  public String getSource() {
    return source;
  }

  public void setSource(final String source) {
    this.source = source;
  }

  public String getEventName() {
    return eventName;
  }

  public void setEventName(final String eventName) {
    this.eventName = eventName;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final Long timestamp) {
    this.timestamp = timestamp;
  }

  public Long getOrderCounter() {
    return orderCounter;
  }

  public void setOrderCounter(final Long orderCounter) {
    this.orderCounter = orderCounter;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof TracedEventDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $eventId = getEventId();
    result = result * PRIME + ($eventId == null ? 43 : $eventId.hashCode());
    final Object $group = getGroup();
    result = result * PRIME + ($group == null ? 43 : $group.hashCode());
    final Object $source = getSource();
    result = result * PRIME + ($source == null ? 43 : $source.hashCode());
    final Object $eventName = getEventName();
    result = result * PRIME + ($eventName == null ? 43 : $eventName.hashCode());
    final Object $timestamp = getTimestamp();
    result = result * PRIME + ($timestamp == null ? 43 : $timestamp.hashCode());
    final Object $orderCounter = getOrderCounter();
    result = result * PRIME + ($orderCounter == null ? 43 : $orderCounter.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TracedEventDto)) {
      return false;
    }
    final TracedEventDto other = (TracedEventDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$eventId = getEventId();
    final Object other$eventId = other.getEventId();
    if (this$eventId == null ? other$eventId != null : !this$eventId.equals(other$eventId)) {
      return false;
    }
    final Object this$group = getGroup();
    final Object other$group = other.getGroup();
    if (this$group == null ? other$group != null : !this$group.equals(other$group)) {
      return false;
    }
    final Object this$source = getSource();
    final Object other$source = other.getSource();
    if (this$source == null ? other$source != null : !this$source.equals(other$source)) {
      return false;
    }
    final Object this$eventName = getEventName();
    final Object other$eventName = other.getEventName();
    if (this$eventName == null
        ? other$eventName != null
        : !this$eventName.equals(other$eventName)) {
      return false;
    }
    final Object this$timestamp = getTimestamp();
    final Object other$timestamp = other.getTimestamp();
    if (this$timestamp == null
        ? other$timestamp != null
        : !this$timestamp.equals(other$timestamp)) {
      return false;
    }
    final Object this$orderCounter = getOrderCounter();
    final Object other$orderCounter = other.getOrderCounter();
    if (this$orderCounter == null
        ? other$orderCounter != null
        : !this$orderCounter.equals(other$orderCounter)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "TracedEventDto(eventId="
        + getEventId()
        + ", group="
        + getGroup()
        + ", source="
        + getSource()
        + ", eventName="
        + getEventName()
        + ", timestamp="
        + getTimestamp()
        + ", orderCounter="
        + getOrderCounter()
        + ")";
  }

  public static TracedEventDtoBuilder builder() {
    return new TracedEventDtoBuilder();
  }

  public static final class Fields {

    public static final String eventId = "eventId";
    public static final String group = "group";
    public static final String source = "source";
    public static final String eventName = "eventName";
    public static final String timestamp = "timestamp";
    public static final String orderCounter = "orderCounter";
  }

  public static class TracedEventDtoBuilder {

    private String eventId;
    private String group;
    private String source;
    private String eventName;
    private Long timestamp;
    private Long orderCounter;

    TracedEventDtoBuilder() {}

    public TracedEventDtoBuilder eventId(final String eventId) {
      this.eventId = eventId;
      return this;
    }

    public TracedEventDtoBuilder group(final String group) {
      this.group = group;
      return this;
    }

    public TracedEventDtoBuilder source(final String source) {
      this.source = source;
      return this;
    }

    public TracedEventDtoBuilder eventName(final String eventName) {
      this.eventName = eventName;
      return this;
    }

    public TracedEventDtoBuilder timestamp(final Long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public TracedEventDtoBuilder orderCounter(final Long orderCounter) {
      this.orderCounter = orderCounter;
      return this;
    }

    public TracedEventDto build() {
      return new TracedEventDto(eventId, group, source, eventName, timestamp, orderCounter);
    }

    @Override
    public String toString() {
      return "TracedEventDto.TracedEventDtoBuilder(eventId="
          + eventId
          + ", group="
          + group
          + ", source="
          + source
          + ", eventName="
          + eventName
          + ", timestamp="
          + timestamp
          + ", orderCounter="
          + orderCounter
          + ")";
    }
  }
}
