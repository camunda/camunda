/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import jakarta.validation.constraints.NotBlank;

public class EventTypeDto implements OptimizeDto {

  private String group;
  private String source;

  @NotBlank private String eventName;

  private String eventLabel;

  public EventTypeDto(
      final String group,
      final String source,
      @NotBlank final String eventName,
      final String eventLabel) {
    this.group = group;
    this.source = source;
    this.eventName = eventName;
    this.eventLabel = eventLabel;
  }

  protected EventTypeDto() {}

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

  public @NotBlank String getEventName() {
    return eventName;
  }

  public void setEventName(@NotBlank final String eventName) {
    this.eventName = eventName;
  }

  public String getEventLabel() {
    return eventLabel;
  }

  public void setEventLabel(final String eventLabel) {
    this.eventLabel = eventLabel;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventTypeDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $group = getGroup();
    result = result * PRIME + ($group == null ? 43 : $group.hashCode());
    final Object $source = getSource();
    result = result * PRIME + ($source == null ? 43 : $source.hashCode());
    final Object $eventName = getEventName();
    result = result * PRIME + ($eventName == null ? 43 : $eventName.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventTypeDto)) {
      return false;
    }
    final EventTypeDto other = (EventTypeDto) o;
    if (!other.canEqual((Object) this)) {
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
    return true;
  }

  @Override
  public String toString() {
    return "EventTypeDto(group="
        + getGroup()
        + ", source="
        + getSource()
        + ", eventName="
        + getEventName()
        + ", eventLabel="
        + getEventLabel()
        + ")";
  }

  public static EventTypeDtoBuilder builder() {
    return new EventTypeDtoBuilder();
  }

  public EventTypeDtoBuilder toBuilder() {
    return new EventTypeDtoBuilder()
        .group(group)
        .source(source)
        .eventName(eventName)
        .eventLabel(eventLabel);
  }

  public static final class Fields {

    public static final String group = "group";
    public static final String source = "source";
    public static final String eventName = "eventName";
    public static final String eventLabel = "eventLabel";
  }

  public static class EventTypeDtoBuilder {

    private String group;
    private String source;
    private @NotBlank String eventName;
    private String eventLabel;

    EventTypeDtoBuilder() {}

    public EventTypeDtoBuilder group(final String group) {
      this.group = group;
      return this;
    }

    public EventTypeDtoBuilder source(final String source) {
      this.source = source;
      return this;
    }

    public EventTypeDtoBuilder eventName(@NotBlank final String eventName) {
      this.eventName = eventName;
      return this;
    }

    public EventTypeDtoBuilder eventLabel(final String eventLabel) {
      this.eventLabel = eventLabel;
      return this;
    }

    public EventTypeDto build() {
      return new EventTypeDto(group, source, eventName, eventLabel);
    }

    @Override
    public String toString() {
      return "EventTypeDto.EventTypeDtoBuilder(group="
          + group
          + ", source="
          + source
          + ", eventName="
          + eventName
          + ", eventLabel="
          + eventLabel
          + ")";
    }
  }
}
