/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event;

import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import java.time.Instant;

public class DeletableEventDto {

  private String id;
  private String traceId;
  private String group;
  private String source;
  private String eventName;
  private Instant timestamp;

  public DeletableEventDto(final String id, final String traceId, final String group,
      final String source, final String eventName,
      final Instant timestamp) {
    this.id = id;
    this.traceId = traceId;
    this.group = group;
    this.source = source;
    this.eventName = eventName;
    this.timestamp = timestamp;
  }

  public DeletableEventDto() {
  }

  public static DeletableEventDto from(final EventDto eventDto) {
    return new DeletableEventDto(
        eventDto.getId(),
        eventDto.getTraceId(),
        eventDto.getGroup(),
        eventDto.getSource(),
        eventDto.getEventName(),
        Instant.ofEpochMilli(eventDto.getTimestamp()));
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(final String traceId) {
    this.traceId = traceId;
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

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final Instant timestamp) {
    this.timestamp = timestamp;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DeletableEventDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $traceId = getTraceId();
    result = result * PRIME + ($traceId == null ? 43 : $traceId.hashCode());
    final Object $group = getGroup();
    result = result * PRIME + ($group == null ? 43 : $group.hashCode());
    final Object $source = getSource();
    result = result * PRIME + ($source == null ? 43 : $source.hashCode());
    final Object $eventName = getEventName();
    result = result * PRIME + ($eventName == null ? 43 : $eventName.hashCode());
    final Object $timestamp = getTimestamp();
    result = result * PRIME + ($timestamp == null ? 43 : $timestamp.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DeletableEventDto)) {
      return false;
    }
    final DeletableEventDto other = (DeletableEventDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$traceId = getTraceId();
    final Object other$traceId = other.getTraceId();
    if (this$traceId == null ? other$traceId != null : !this$traceId.equals(other$traceId)) {
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
    if (this$eventName == null ? other$eventName != null
        : !this$eventName.equals(other$eventName)) {
      return false;
    }
    final Object this$timestamp = getTimestamp();
    final Object other$timestamp = other.getTimestamp();
    if (this$timestamp == null ? other$timestamp != null
        : !this$timestamp.equals(other$timestamp)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DeletableEventDto(id=" + getId() + ", traceId=" + getTraceId() + ", group="
        + getGroup() + ", source=" + getSource() + ", eventName=" + getEventName()
        + ", timestamp=" + getTimestamp() + ")";
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String traceId = "traceId";
    public static final String group = "group";
    public static final String source = "source";
    public static final String eventName = "eventName";
    public static final String timestamp = "timestamp";
  }
}
