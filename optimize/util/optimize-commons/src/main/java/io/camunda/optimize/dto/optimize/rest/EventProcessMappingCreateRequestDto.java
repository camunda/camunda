/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EventProcessMappingCreateRequestDto extends EventProcessMappingRequestDto {

  private static final String DEFAULT_PROCESS_NAME = "New Process";
  private boolean autogenerate = false;

  public EventProcessMappingCreateRequestDto(
      final String name,
      final String xml,
      final Map<String, EventMappingDto> mappings,
      final List<EventSourceEntryDto<?>> eventSources,
      final boolean autogenerate) {
    super(name, xml, mappings, eventSources);
    this.autogenerate = autogenerate;
  }

  public EventProcessMappingCreateRequestDto(final boolean autogenerate) {
    this.autogenerate = autogenerate;
  }

  public EventProcessMappingCreateRequestDto() {}

  public static EventProcessMappingDto to(
      final String userId, final EventProcessMappingCreateRequestDto createRequestDto) {
    return EventProcessMappingDto.builder()
        .name(Optional.ofNullable(createRequestDto.getName()).orElse(DEFAULT_PROCESS_NAME))
        .xml(createRequestDto.getXml())
        .mappings(createRequestDto.getMappings())
        .lastModifier(userId)
        .eventSources(createRequestDto.getEventSources())
        .roles(
            Collections.singletonList(
                new EventProcessRoleRequestDto<>(new IdentityDto(userId, IdentityType.USER))))
        .build();
  }

  public boolean isAutogenerate() {
    return autogenerate;
  }

  public void setAutogenerate(final boolean autogenerate) {
    this.autogenerate = autogenerate;
  }

  @Override
  public String toString() {
    return "EventProcessMappingCreateRequestDto(autogenerate=" + isAutogenerate() + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventProcessMappingCreateRequestDto)) {
      return false;
    }
    final EventProcessMappingCreateRequestDto other = (EventProcessMappingCreateRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    if (isAutogenerate() != other.isAutogenerate()) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof EventProcessMappingCreateRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    result = result * PRIME + (isAutogenerate() ? 79 : 97);
    return result;
  }

  public static EventProcessMappingCreateRequestDtoBuilder eventProcessMappingCreateBuilder() {
    return new EventProcessMappingCreateRequestDtoBuilder();
  }

  public static class EventProcessMappingCreateRequestDtoBuilder {

    private String name;
    private String xml;
    private Map<String, EventMappingDto> mappings;
    private List<EventSourceEntryDto<?>> eventSources;
    private boolean autogenerate;

    EventProcessMappingCreateRequestDtoBuilder() {}

    public EventProcessMappingCreateRequestDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public EventProcessMappingCreateRequestDtoBuilder xml(final String xml) {
      this.xml = xml;
      return this;
    }

    public EventProcessMappingCreateRequestDtoBuilder mappings(
        final Map<String, EventMappingDto> mappings) {
      this.mappings = mappings;
      return this;
    }

    public EventProcessMappingCreateRequestDtoBuilder eventSources(
        final List<EventSourceEntryDto<?>> eventSources) {
      this.eventSources = eventSources;
      return this;
    }

    public EventProcessMappingCreateRequestDtoBuilder autogenerate(final boolean autogenerate) {
      this.autogenerate = autogenerate;
      return this;
    }

    public EventProcessMappingCreateRequestDto build() {
      return new EventProcessMappingCreateRequestDto(
          name, xml, mappings, eventSources, autogenerate);
    }

    @Override
    public String toString() {
      return "EventProcessMappingCreateRequestDto.EventProcessMappingCreateRequestDtoBuilder(name="
          + name
          + ", xml="
          + xml
          + ", mappings="
          + mappings
          + ", eventSources="
          + eventSources
          + ", autogenerate="
          + autogenerate
          + ")";
    }
  }
}
