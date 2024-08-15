/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventMappingCleanupRequestDto {

  @NotNull private String xml;

  @NotNull private Map<String, EventMappingDto> mappings = new HashMap<>();

  @NotNull private List<EventSourceEntryDto<?>> eventSources = new ArrayList<>();

  public EventMappingCleanupRequestDto(
      @NotNull final String xml,
      @NotNull final Map<String, EventMappingDto> mappings,
      @NotNull final List<EventSourceEntryDto<?>> eventSources) {
    this.xml = xml;
    this.mappings = mappings;
    this.eventSources = eventSources;
  }

  protected EventMappingCleanupRequestDto() {}

  public @NotNull String getXml() {
    return xml;
  }

  public void setXml(@NotNull final String xml) {
    this.xml = xml;
  }

  public @NotNull Map<String, EventMappingDto> getMappings() {
    return mappings;
  }

  public void setMappings(@NotNull final Map<String, EventMappingDto> mappings) {
    this.mappings = mappings;
  }

  public @NotNull List<EventSourceEntryDto<?>> getEventSources() {
    return eventSources;
  }

  public void setEventSources(@NotNull final List<EventSourceEntryDto<?>> eventSources) {
    this.eventSources = eventSources;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventMappingCleanupRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $xml = getXml();
    result = result * PRIME + ($xml == null ? 43 : $xml.hashCode());
    final Object $mappings = getMappings();
    result = result * PRIME + ($mappings == null ? 43 : $mappings.hashCode());
    final Object $eventSources = getEventSources();
    result = result * PRIME + ($eventSources == null ? 43 : $eventSources.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventMappingCleanupRequestDto)) {
      return false;
    }
    final EventMappingCleanupRequestDto other = (EventMappingCleanupRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$xml = getXml();
    final Object other$xml = other.getXml();
    if (this$xml == null ? other$xml != null : !this$xml.equals(other$xml)) {
      return false;
    }
    final Object this$mappings = getMappings();
    final Object other$mappings = other.getMappings();
    if (this$mappings == null ? other$mappings != null : !this$mappings.equals(other$mappings)) {
      return false;
    }
    final Object this$eventSources = getEventSources();
    final Object other$eventSources = other.getEventSources();
    if (this$eventSources == null
        ? other$eventSources != null
        : !this$eventSources.equals(other$eventSources)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventMappingCleanupRequestDto(xml="
        + getXml()
        + ", mappings="
        + getMappings()
        + ", eventSources="
        + getEventSources()
        + ")";
  }

  @NotNull
  private static Map<String, EventMappingDto> $default$mappings() {
    return new HashMap<>();
  }

  @NotNull
  private static List<EventSourceEntryDto<?>> $default$eventSources() {
    return new ArrayList<>();
  }

  public static EventMappingCleanupRequestDtoBuilder builder() {
    return new EventMappingCleanupRequestDtoBuilder();
  }

  public static class EventMappingCleanupRequestDtoBuilder {

    private @NotNull String xml;
    private @NotNull Map<String, EventMappingDto> mappings$value;
    private boolean mappings$set;
    private @NotNull List<EventSourceEntryDto<?>> eventSources$value;
    private boolean eventSources$set;

    EventMappingCleanupRequestDtoBuilder() {}

    public EventMappingCleanupRequestDtoBuilder xml(@NotNull final String xml) {
      this.xml = xml;
      return this;
    }

    public EventMappingCleanupRequestDtoBuilder mappings(
        @NotNull final Map<String, EventMappingDto> mappings) {
      mappings$value = mappings;
      mappings$set = true;
      return this;
    }

    public EventMappingCleanupRequestDtoBuilder eventSources(
        @NotNull final List<EventSourceEntryDto<?>> eventSources) {
      eventSources$value = eventSources;
      eventSources$set = true;
      return this;
    }

    public EventMappingCleanupRequestDto build() {
      Map<String, EventMappingDto> mappings$value = this.mappings$value;
      if (!mappings$set) {
        mappings$value = EventMappingCleanupRequestDto.$default$mappings();
      }
      List<EventSourceEntryDto<?>> eventSources$value = this.eventSources$value;
      if (!eventSources$set) {
        eventSources$value = EventMappingCleanupRequestDto.$default$eventSources();
      }
      return new EventMappingCleanupRequestDto(xml, mappings$value, eventSources$value);
    }

    @Override
    public String toString() {
      return "EventMappingCleanupRequestDto.EventMappingCleanupRequestDtoBuilder(xml="
          + xml
          + ", mappings$value="
          + mappings$value
          + ", eventSources$value="
          + eventSources$value
          + ")";
    }
  }
}
