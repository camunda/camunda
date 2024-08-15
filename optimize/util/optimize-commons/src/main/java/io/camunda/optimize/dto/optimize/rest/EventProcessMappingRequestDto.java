/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EventProcessMappingRequestDto {

  private String name;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String xml;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Valid
  private Map<String, EventMappingDto> mappings;

  @Valid
  private List<EventSourceEntryDto<?>> eventSources = new ArrayList<>();

  public EventProcessMappingRequestDto(final String name, final String xml,
      @Valid final Map<String, EventMappingDto> mappings,
      @Valid final List<EventSourceEntryDto<?>> eventSources) {
    this.name = name;
    this.xml = xml;
    this.mappings = mappings;
    this.eventSources = eventSources;
  }

  public EventProcessMappingRequestDto() {
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getXml() {
    return xml;
  }

  public void setXml(final String xml) {
    this.xml = xml;
  }

  public @Valid Map<String, EventMappingDto> getMappings() {
    return mappings;
  }

  public void setMappings(@Valid final Map<String, EventMappingDto> mappings) {
    this.mappings = mappings;
  }

  public @Valid List<EventSourceEntryDto<?>> getEventSources() {
    return eventSources;
  }

  public void setEventSources(@Valid final List<EventSourceEntryDto<?>> eventSources) {
    this.eventSources = eventSources;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventProcessMappingRequestDto;
  }

  @Override
  public int hashCode() {
    final int result = 1;
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventProcessMappingRequestDto)) {
      return false;
    }
    final EventProcessMappingRequestDto other = (EventProcessMappingRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventProcessMappingRequestDto(name=" + getName() + ", xml=" + getXml()
        + ", mappings=" + getMappings() + ", eventSources=" + getEventSources() + ")";
  }

  @Valid
  private static List<EventSourceEntryDto<?>> $default$eventSources() {
    return new ArrayList<>();
  }

  public static EventProcessMappingRequestDtoBuilder builder() {
    return new EventProcessMappingRequestDtoBuilder();
  }

  public static final class Fields {

    public static final String name = "name";
    public static final String xml = "xml";
    public static final String mappings = "mappings";
    public static final String eventSources = "eventSources";
  }

  public static class EventProcessMappingRequestDtoBuilder {

    private String name;
    private String xml;
    private @Valid Map<String, EventMappingDto> mappings;
    private @Valid List<EventSourceEntryDto<?>> eventSources$value;
    private boolean eventSources$set;

    EventProcessMappingRequestDtoBuilder() {
    }

    public EventProcessMappingRequestDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public EventProcessMappingRequestDtoBuilder xml(final String xml) {
      this.xml = xml;
      return this;
    }

    public EventProcessMappingRequestDtoBuilder mappings(
        @Valid final Map<String, EventMappingDto> mappings) {
      this.mappings = mappings;
      return this;
    }

    public EventProcessMappingRequestDtoBuilder eventSources(
        @Valid final List<EventSourceEntryDto<?>> eventSources) {
      eventSources$value = eventSources;
      eventSources$set = true;
      return this;
    }

    public EventProcessMappingRequestDto build() {
      List<EventSourceEntryDto<?>> eventSources$value = this.eventSources$value;
      if (!eventSources$set) {
        eventSources$value = EventProcessMappingRequestDto.$default$eventSources();
      }
      return new EventProcessMappingRequestDto(name, xml, mappings,
          eventSources$value);
    }

    @Override
    public String toString() {
      return "EventProcessMappingRequestDto.EventProcessMappingRequestDtoBuilder(name=" + name
          + ", xml=" + xml + ", mappings=" + mappings + ", eventSources$value="
          + eventSources$value + ")";
    }
  }
}
