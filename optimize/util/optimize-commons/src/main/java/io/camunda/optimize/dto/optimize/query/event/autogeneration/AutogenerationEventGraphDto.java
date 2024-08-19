/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.autogeneration;

import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import java.util.List;
import java.util.Map;

public class AutogenerationEventGraphDto {

  private final List<EventTypeDto> startEvents;
  private final List<EventTypeDto> endEvents;
  private final Map<EventTypeDto, AutogenerationAdjacentEventTypesDto> adjacentEventTypesDtoMap;

  AutogenerationEventGraphDto(
      final List<EventTypeDto> startEvents,
      final List<EventTypeDto> endEvents,
      final Map<EventTypeDto, AutogenerationAdjacentEventTypesDto> adjacentEventTypesDtoMap) {
    this.startEvents = startEvents;
    this.endEvents = endEvents;
    this.adjacentEventTypesDtoMap = adjacentEventTypesDtoMap;
  }

  public List<EventTypeDto> getStartEvents() {
    return startEvents;
  }

  public List<EventTypeDto> getEndEvents() {
    return endEvents;
  }

  public Map<EventTypeDto, AutogenerationAdjacentEventTypesDto> getAdjacentEventTypesDtoMap() {
    return adjacentEventTypesDtoMap;
  }

  public static AutogenerationEventGraphDtoBuilder builder() {
    return new AutogenerationEventGraphDtoBuilder();
  }

  public static class AutogenerationEventGraphDtoBuilder {

    private List<EventTypeDto> startEvents;
    private List<EventTypeDto> endEvents;
    private Map<EventTypeDto, AutogenerationAdjacentEventTypesDto> adjacentEventTypesDtoMap;

    AutogenerationEventGraphDtoBuilder() {}

    public AutogenerationEventGraphDtoBuilder startEvents(final List<EventTypeDto> startEvents) {
      this.startEvents = startEvents;
      return this;
    }

    public AutogenerationEventGraphDtoBuilder endEvents(final List<EventTypeDto> endEvents) {
      this.endEvents = endEvents;
      return this;
    }

    public AutogenerationEventGraphDtoBuilder adjacentEventTypesDtoMap(
        final Map<EventTypeDto, AutogenerationAdjacentEventTypesDto> adjacentEventTypesDtoMap) {
      this.adjacentEventTypesDtoMap = adjacentEventTypesDtoMap;
      return this;
    }

    public AutogenerationEventGraphDto build() {
      return new AutogenerationEventGraphDto(startEvents, endEvents, adjacentEventTypesDtoMap);
    }

    @Override
    public String toString() {
      return "AutogenerationEventGraphDto.AutogenerationEventGraphDtoBuilder(startEvents="
          + startEvents
          + ", endEvents="
          + endEvents
          + ", adjacentEventTypesDtoMap="
          + adjacentEventTypesDtoMap
          + ")";
    }
  }
}
