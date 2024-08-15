/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import jakarta.validation.Valid;

public class EventMappingDto implements OptimizeDto {

  @Valid
  EventTypeDto start;
  @Valid
  EventTypeDto end;

  public EventMappingDto(@Valid final EventTypeDto start, @Valid final EventTypeDto end) {
    this.start = start;
    this.end = end;
  }

  public EventMappingDto() {
  }

  public @Valid EventTypeDto getStart() {
    return start;
  }

  public void setStart(@Valid final EventTypeDto start) {
    this.start = start;
  }

  public @Valid EventTypeDto getEnd() {
    return end;
  }

  public void setEnd(@Valid final EventTypeDto end) {
    this.end = end;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventMappingDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $start = getStart();
    result = result * PRIME + ($start == null ? 43 : $start.hashCode());
    final Object $end = getEnd();
    result = result * PRIME + ($end == null ? 43 : $end.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventMappingDto)) {
      return false;
    }
    final EventMappingDto other = (EventMappingDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$start = getStart();
    final Object other$start = other.getStart();
    if (this$start == null ? other$start != null : !this$start.equals(other$start)) {
      return false;
    }
    final Object this$end = getEnd();
    final Object other$end = other.getEnd();
    if (this$end == null ? other$end != null : !this$end.equals(other$end)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventMappingDto(start=" + getStart() + ", end=" + getEnd() + ")";
  }

  public static EventMappingDtoBuilder builder() {
    return new EventMappingDtoBuilder();
  }

  public static final class Fields {

    public static final String start = "start";
    public static final String end = "end";
  }

  public static class EventMappingDtoBuilder {

    private @Valid EventTypeDto start;
    private @Valid EventTypeDto end;

    EventMappingDtoBuilder() {
    }

    public EventMappingDtoBuilder start(@Valid final EventTypeDto start) {
      this.start = start;
      return this;
    }

    public EventMappingDtoBuilder end(@Valid final EventTypeDto end) {
      this.end = end;
      return this;
    }

    public EventMappingDto build() {
      return new EventMappingDto(start, end);
    }

    @Override
    public String toString() {
      return "EventMappingDto.EventMappingDtoBuilder(start=" + start + ", end=" + end
          + ")";
    }
  }
}
