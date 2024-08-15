/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.sequence;

import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventCountRequestDto {

  @Size(min = 1)
  private String targetFlowNodeId;

  @Size(min = 1)
  private String xml;
  private Map<String, EventMappingDto> mappings = new HashMap<>();
  private List<EventSourceEntryDto<?>> eventSources = new ArrayList<>();

  public EventCountRequestDto(
      @Size(min = 1) final String targetFlowNodeId,
      @Size(min = 1) final String xml,
      final Map<String, EventMappingDto> mappings,
      final List<EventSourceEntryDto<?>> eventSources) {
    this.targetFlowNodeId = targetFlowNodeId;
    this.xml = xml;
    if (mappings != null) {
      throw new IllegalArgumentException("mappings cannot be null");
    }

    this.mappings = mappings;
    if (eventSources != null) {
      throw new IllegalArgumentException("eventSources cannot be null");
    }

    this.eventSources = eventSources;
  }

  protected EventCountRequestDto() {
  }

  public @Size(min = 1) String getTargetFlowNodeId() {
    return targetFlowNodeId;
  }

  public void setTargetFlowNodeId(@Size(min = 1) final String targetFlowNodeId) {
    this.targetFlowNodeId = targetFlowNodeId;
  }

  public @Size(min = 1) String getXml() {
    return xml;
  }

  public void setXml(@Size(min = 1) final String xml) {
    this.xml = xml;
  }

  public Map<String, EventMappingDto> getMappings() {
    return mappings;
  }

  public void setMappings(final Map<String, EventMappingDto> mappings) {
    if (mappings == null) {
      throw new IllegalArgumentException("mappings cannot be null");
    }

    this.mappings = mappings;
  }

  public List<EventSourceEntryDto<?>> getEventSources() {
    if (eventSources == null) {
      throw new IllegalArgumentException("eventSources cannot be null");
    }

    return eventSources;
  }

  public void setEventSources(final List<EventSourceEntryDto<?>> eventSources) {
    if (eventSources == null) {
      throw new IllegalArgumentException("eventSources cannot be null");
    }

    this.eventSources = eventSources;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventCountRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $targetFlowNodeId = getTargetFlowNodeId();
    result = result * PRIME + ($targetFlowNodeId == null ? 43 : $targetFlowNodeId.hashCode());
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
    if (!(o instanceof EventCountRequestDto)) {
      return false;
    }
    final EventCountRequestDto other = (EventCountRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$targetFlowNodeId = getTargetFlowNodeId();
    final Object other$targetFlowNodeId = other.getTargetFlowNodeId();
    if (this$targetFlowNodeId == null ? other$targetFlowNodeId != null
        : !this$targetFlowNodeId.equals(other$targetFlowNodeId)) {
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
    if (this$eventSources == null ? other$eventSources != null
        : !this$eventSources.equals(other$eventSources)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventCountRequestDto(targetFlowNodeId=" + getTargetFlowNodeId() + ", xml="
        + getXml() + ", mappings=" + getMappings() + ", eventSources="
        + getEventSources() + ")";
  }

  private static Map<String, EventMappingDto> $default$mappings() {
    return new HashMap<>();
  }

  private static List<EventSourceEntryDto<?>> $default$eventSources() {
    return new ArrayList<>();
  }

  public static EventCountRequestDtoBuilder builder() {
    return new EventCountRequestDtoBuilder();
  }

  public static class EventCountRequestDtoBuilder {

    private @Size(min = 1) String targetFlowNodeId;
    private @Size(min = 1) String xml;
    private Map<String, EventMappingDto> mappings$value;
    private boolean mappings$set;
    private List<EventSourceEntryDto<?>> eventSources$value;
    private boolean eventSources$set;

    EventCountRequestDtoBuilder() {
    }

    public EventCountRequestDtoBuilder targetFlowNodeId(
        @Size(min = 1) final String targetFlowNodeId) {
      this.targetFlowNodeId = targetFlowNodeId;
      return this;
    }

    public EventCountRequestDtoBuilder xml(@Size(min = 1) final String xml) {
      this.xml = xml;
      return this;
    }

    public EventCountRequestDtoBuilder mappings(final Map<String, EventMappingDto> mappings) {
      if (mappings == null) {
        throw new IllegalArgumentException("mappings cannot be null");
      }

      mappings$value = mappings;
      mappings$set = true;
      return this;
    }

    public EventCountRequestDtoBuilder eventSources(
        final List<EventSourceEntryDto<?>> eventSources) {
      if (eventSources == null) {
        throw new IllegalArgumentException("eventSources cannot be null");
      }

      eventSources$value = eventSources;
      eventSources$set = true;
      return this;
    }

    public EventCountRequestDto build() {
      Map<String, EventMappingDto> mappings$value = this.mappings$value;
      if (!mappings$set) {
        mappings$value = EventCountRequestDto.$default$mappings();
      }
      List<EventSourceEntryDto<?>> eventSources$value = this.eventSources$value;
      if (!eventSources$set) {
        eventSources$value = EventCountRequestDto.$default$eventSources();
      }
      return new EventCountRequestDto(targetFlowNodeId, xml, mappings$value,
          eventSources$value);
    }

    @Override
    public String toString() {
      return "EventCountRequestDto.EventCountRequestDtoBuilder(targetFlowNodeId="
          + targetFlowNodeId + ", xml=" + xml + ", mappings$value=" + mappings$value
          + ", eventSources$value=" + eventSources$value + ")";
    }
  }
}
