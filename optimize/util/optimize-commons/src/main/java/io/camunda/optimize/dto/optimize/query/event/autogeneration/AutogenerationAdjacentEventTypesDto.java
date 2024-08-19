/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.autogeneration;

import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import java.util.ArrayList;
import java.util.List;

public class AutogenerationAdjacentEventTypesDto {

  private List<EventTypeDto> precedingEvents = new ArrayList<>();
  private List<EventTypeDto> succeedingEvents = new ArrayList<>();

  AutogenerationAdjacentEventTypesDto(
      final List<EventTypeDto> precedingEvents, final List<EventTypeDto> succeedingEvents) {
    this.precedingEvents = precedingEvents;
    this.succeedingEvents = succeedingEvents;
  }

  public List<EventTypeDto> getPrecedingEvents() {
    return precedingEvents;
  }

  public void setPrecedingEvents(final List<EventTypeDto> precedingEvents) {
    this.precedingEvents = precedingEvents;
  }

  public List<EventTypeDto> getSucceedingEvents() {
    return succeedingEvents;
  }

  public void setSucceedingEvents(final List<EventTypeDto> succeedingEvents) {
    this.succeedingEvents = succeedingEvents;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AutogenerationAdjacentEventTypesDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $precedingEvents = getPrecedingEvents();
    result = result * PRIME + ($precedingEvents == null ? 43 : $precedingEvents.hashCode());
    final Object $succeedingEvents = getSucceedingEvents();
    result = result * PRIME + ($succeedingEvents == null ? 43 : $succeedingEvents.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AutogenerationAdjacentEventTypesDto)) {
      return false;
    }
    final AutogenerationAdjacentEventTypesDto other = (AutogenerationAdjacentEventTypesDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$precedingEvents = getPrecedingEvents();
    final Object other$precedingEvents = other.getPrecedingEvents();
    if (this$precedingEvents == null
        ? other$precedingEvents != null
        : !this$precedingEvents.equals(other$precedingEvents)) {
      return false;
    }
    final Object this$succeedingEvents = getSucceedingEvents();
    final Object other$succeedingEvents = other.getSucceedingEvents();
    if (this$succeedingEvents == null
        ? other$succeedingEvents != null
        : !this$succeedingEvents.equals(other$succeedingEvents)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AutogenerationAdjacentEventTypesDto(precedingEvents="
        + getPrecedingEvents()
        + ", succeedingEvents="
        + getSucceedingEvents()
        + ")";
  }

  private static List<EventTypeDto> $default$precedingEvents() {
    return new ArrayList<>();
  }

  private static List<EventTypeDto> $default$succeedingEvents() {
    return new ArrayList<>();
  }

  public static AutogenerationAdjacentEventTypesDtoBuilder builder() {
    return new AutogenerationAdjacentEventTypesDtoBuilder();
  }

  public static class AutogenerationAdjacentEventTypesDtoBuilder {

    private List<EventTypeDto> precedingEvents$value;
    private boolean precedingEvents$set;
    private List<EventTypeDto> succeedingEvents$value;
    private boolean succeedingEvents$set;

    AutogenerationAdjacentEventTypesDtoBuilder() {}

    public AutogenerationAdjacentEventTypesDtoBuilder precedingEvents(
        final List<EventTypeDto> precedingEvents) {
      precedingEvents$value = precedingEvents;
      precedingEvents$set = true;
      return this;
    }

    public AutogenerationAdjacentEventTypesDtoBuilder succeedingEvents(
        final List<EventTypeDto> succeedingEvents) {
      succeedingEvents$value = succeedingEvents;
      succeedingEvents$set = true;
      return this;
    }

    public AutogenerationAdjacentEventTypesDto build() {
      List<EventTypeDto> precedingEvents$value = this.precedingEvents$value;
      if (!precedingEvents$set) {
        precedingEvents$value = AutogenerationAdjacentEventTypesDto.$default$precedingEvents();
      }
      List<EventTypeDto> succeedingEvents$value = this.succeedingEvents$value;
      if (!succeedingEvents$set) {
        succeedingEvents$value = AutogenerationAdjacentEventTypesDto.$default$succeedingEvents();
      }
      return new AutogenerationAdjacentEventTypesDto(precedingEvents$value, succeedingEvents$value);
    }

    @Override
    public String toString() {
      return "AutogenerationAdjacentEventTypesDto.AutogenerationAdjacentEventTypesDtoBuilder(precedingEvents$value="
          + precedingEvents$value
          + ", succeedingEvents$value="
          + succeedingEvents$value
          + ")";
    }
  }
}
