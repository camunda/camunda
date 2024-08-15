/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EventProcessMappingResponseDto {

  private String id;
  @NotBlank private String name;

  private String lastModifier;

  private OffsetDateTime lastModified;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String xml;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Valid
  private Map<String, EventMappingDto> mappings;

  private EventProcessState state;

  private Double publishingProgress;

  private List<EventSourceEntryDto<?>> eventSources = new ArrayList<>();

  public EventProcessMappingResponseDto(
      final String id,
      @NotBlank final String name,
      final String lastModifier,
      final OffsetDateTime lastModified,
      final String xml,
      @Valid final Map<String, EventMappingDto> mappings,
      final EventProcessState state,
      final Double publishingProgress,
      final List<EventSourceEntryDto<?>> eventSources) {
    this.id = id;
    this.name = name;
    this.lastModifier = lastModifier;
    this.lastModified = lastModified;
    this.xml = xml;
    this.mappings = mappings;
    this.state = state;
    this.publishingProgress = publishingProgress;
    this.eventSources = eventSources;
  }

  public EventProcessMappingResponseDto() {}

  public static EventProcessMappingResponseDto from(
      final EventProcessMappingDto dto,
      final String lastModifierName,
      final List<EventSourceEntryDto<?>> eventSourcesDtos) {
    return EventProcessMappingResponseDto.builder()
        .id(dto.getId())
        .lastModified(dto.getLastModified())
        .lastModifier(lastModifierName)
        .mappings(dto.getMappings())
        .name(dto.getName())
        .state(dto.getState())
        .publishingProgress(dto.getPublishingProgress())
        .xml(dto.getXml())
        .eventSources(eventSourcesDtos)
        .build();
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public @NotBlank String getName() {
    return name;
  }

  public void setName(@NotBlank final String name) {
    this.name = name;
  }

  public String getLastModifier() {
    return lastModifier;
  }

  public void setLastModifier(final String lastModifier) {
    this.lastModifier = lastModifier;
  }

  public OffsetDateTime getLastModified() {
    return lastModified;
  }

  public void setLastModified(final OffsetDateTime lastModified) {
    this.lastModified = lastModified;
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

  public EventProcessState getState() {
    return state;
  }

  public void setState(final EventProcessState state) {
    this.state = state;
  }

  public Double getPublishingProgress() {
    return publishingProgress;
  }

  public void setPublishingProgress(final Double publishingProgress) {
    this.publishingProgress = publishingProgress;
  }

  public List<EventSourceEntryDto<?>> getEventSources() {
    return eventSources;
  }

  public void setEventSources(final List<EventSourceEntryDto<?>> eventSources) {
    this.eventSources = eventSources;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventProcessMappingResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventProcessMappingResponseDto)) {
      return false;
    }
    final EventProcessMappingResponseDto other = (EventProcessMappingResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventProcessMappingResponseDto(id="
        + getId()
        + ", name="
        + getName()
        + ", lastModifier="
        + getLastModifier()
        + ", lastModified="
        + getLastModified()
        + ", xml="
        + getXml()
        + ", mappings="
        + getMappings()
        + ", state="
        + getState()
        + ", publishingProgress="
        + getPublishingProgress()
        + ", eventSources="
        + getEventSources()
        + ")";
  }

  private static List<EventSourceEntryDto<?>> $default$eventSources() {
    return new ArrayList<>();
  }

  public static EventProcessMappingResponseDtoBuilder builder() {
    return new EventProcessMappingResponseDtoBuilder();
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String name = "name";
    public static final String lastModifier = "lastModifier";
    public static final String lastModified = "lastModified";
    public static final String xml = "xml";
    public static final String mappings = "mappings";
    public static final String state = "state";
    public static final String publishingProgress = "publishingProgress";
    public static final String eventSources = "eventSources";
  }

  public static class EventProcessMappingResponseDtoBuilder {

    private String id;
    private @NotBlank String name;
    private String lastModifier;
    private OffsetDateTime lastModified;
    private String xml;
    private @Valid Map<String, EventMappingDto> mappings;
    private EventProcessState state;
    private Double publishingProgress;
    private List<EventSourceEntryDto<?>> eventSources$value;
    private boolean eventSources$set;

    EventProcessMappingResponseDtoBuilder() {}

    public EventProcessMappingResponseDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public EventProcessMappingResponseDtoBuilder name(@NotBlank final String name) {
      this.name = name;
      return this;
    }

    public EventProcessMappingResponseDtoBuilder lastModifier(final String lastModifier) {
      this.lastModifier = lastModifier;
      return this;
    }

    public EventProcessMappingResponseDtoBuilder lastModified(final OffsetDateTime lastModified) {
      this.lastModified = lastModified;
      return this;
    }

    public EventProcessMappingResponseDtoBuilder xml(final String xml) {
      this.xml = xml;
      return this;
    }

    public EventProcessMappingResponseDtoBuilder mappings(
        @Valid final Map<String, EventMappingDto> mappings) {
      this.mappings = mappings;
      return this;
    }

    public EventProcessMappingResponseDtoBuilder state(final EventProcessState state) {
      this.state = state;
      return this;
    }

    public EventProcessMappingResponseDtoBuilder publishingProgress(
        final Double publishingProgress) {
      this.publishingProgress = publishingProgress;
      return this;
    }

    public EventProcessMappingResponseDtoBuilder eventSources(
        final List<EventSourceEntryDto<?>> eventSources) {
      eventSources$value = eventSources;
      eventSources$set = true;
      return this;
    }

    public EventProcessMappingResponseDto build() {
      List<EventSourceEntryDto<?>> eventSources$value = this.eventSources$value;
      if (!eventSources$set) {
        eventSources$value = EventProcessMappingResponseDto.$default$eventSources();
      }
      return new EventProcessMappingResponseDto(
          id,
          name,
          lastModifier,
          lastModified,
          xml,
          mappings,
          state,
          publishingProgress,
          eventSources$value);
    }

    @Override
    public String toString() {
      return "EventProcessMappingResponseDto.EventProcessMappingResponseDtoBuilder(id="
          + id
          + ", name="
          + name
          + ", lastModifier="
          + lastModifier
          + ", lastModified="
          + lastModified
          + ", xml="
          + xml
          + ", mappings="
          + mappings
          + ", state="
          + state
          + ", publishingProgress="
          + publishingProgress
          + ", eventSources$value="
          + eventSources$value
          + ")";
    }
  }
}
