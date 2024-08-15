/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.sequence;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import java.util.Optional;

public class EventSequenceCountDto implements OptimizeDto {

  public static final String ID_FIELD_SEPARATOR = ":";
  public static final String ID_EVENT_SEPARATOR = "%";

  String id;
  EventTypeDto sourceEvent;
  EventTypeDto targetEvent;
  Long count;

  public EventSequenceCountDto(
      final String id,
      final EventTypeDto sourceEvent,
      final EventTypeDto targetEvent,
      final Long count) {
    this.id = id;
    this.sourceEvent = sourceEvent;
    this.targetEvent = targetEvent;
    this.count = count;
  }

  public EventSequenceCountDto() {}

  public String getId() {
    if (id == null) {
      generateIdForEventSequenceCountDto();
    }
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void generateIdForEventSequenceCountDto() {
    if (id == null) {
      id =
          generateIdForEventType(sourceEvent)
              + ID_EVENT_SEPARATOR
              + generateIdForEventType(targetEvent);
    }
  }

  private String generateIdForEventType(final EventTypeDto eventTypeDto) {
    final Optional<EventTypeDto> eventType = Optional.ofNullable(eventTypeDto);
    return String.join(
        ID_FIELD_SEPARATOR,
        eventType.map(EventTypeDto::getGroup).orElse(null),
        eventType.map(EventTypeDto::getSource).orElse(null),
        eventType.map(EventTypeDto::getEventName).orElse(null));
  }

  public EventTypeDto getSourceEvent() {
    return sourceEvent;
  }

  public void setSourceEvent(final EventTypeDto sourceEvent) {
    this.sourceEvent = sourceEvent;
  }

  public EventTypeDto getTargetEvent() {
    return targetEvent;
  }

  public void setTargetEvent(final EventTypeDto targetEvent) {
    this.targetEvent = targetEvent;
  }

  public Long getCount() {
    return count;
  }

  public void setCount(final Long count) {
    this.count = count;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventSequenceCountDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $sourceEvent = getSourceEvent();
    result = result * PRIME + ($sourceEvent == null ? 43 : $sourceEvent.hashCode());
    final Object $targetEvent = getTargetEvent();
    result = result * PRIME + ($targetEvent == null ? 43 : $targetEvent.hashCode());
    final Object $count = getCount();
    result = result * PRIME + ($count == null ? 43 : $count.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventSequenceCountDto)) {
      return false;
    }
    final EventSequenceCountDto other = (EventSequenceCountDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$sourceEvent = getSourceEvent();
    final Object other$sourceEvent = other.getSourceEvent();
    if (this$sourceEvent == null
        ? other$sourceEvent != null
        : !this$sourceEvent.equals(other$sourceEvent)) {
      return false;
    }
    final Object this$targetEvent = getTargetEvent();
    final Object other$targetEvent = other.getTargetEvent();
    if (this$targetEvent == null
        ? other$targetEvent != null
        : !this$targetEvent.equals(other$targetEvent)) {
      return false;
    }
    final Object this$count = getCount();
    final Object other$count = other.getCount();
    if (this$count == null ? other$count != null : !this$count.equals(other$count)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventSequenceCountDto(id="
        + getId()
        + ", sourceEvent="
        + getSourceEvent()
        + ", targetEvent="
        + getTargetEvent()
        + ", count="
        + getCount()
        + ")";
  }

  public static EventSequenceCountDtoBuilder builder() {
    return new EventSequenceCountDtoBuilder();
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String sourceEvent = "sourceEvent";
    public static final String targetEvent = "targetEvent";
    public static final String count = "count";
  }

  public static class EventSequenceCountDtoBuilder {

    private String id;
    private EventTypeDto sourceEvent;
    private EventTypeDto targetEvent;
    private Long count;

    EventSequenceCountDtoBuilder() {}

    public EventSequenceCountDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public EventSequenceCountDtoBuilder sourceEvent(final EventTypeDto sourceEvent) {
      this.sourceEvent = sourceEvent;
      return this;
    }

    public EventSequenceCountDtoBuilder targetEvent(final EventTypeDto targetEvent) {
      this.targetEvent = targetEvent;
      return this;
    }

    public EventSequenceCountDtoBuilder count(final Long count) {
      this.count = count;
      return this;
    }

    public EventSequenceCountDto build() {
      return new EventSequenceCountDto(id, sourceEvent, targetEvent, count);
    }

    @Override
    public String toString() {
      return "EventSequenceCountDto.EventSequenceCountDtoBuilder(id="
          + id
          + ", sourceEvent="
          + sourceEvent
          + ", targetEvent="
          + targetEvent
          + ", count="
          + count
          + ")";
    }
  }
}
