/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.sequence;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.util.List;

public class EventTraceStateDto implements OptimizeDto {

  private String traceId;
  private List<TracedEventDto> eventTrace;

  public EventTraceStateDto(final String traceId, final List<TracedEventDto> eventTrace) {
    this.traceId = traceId;
    this.eventTrace = eventTrace;
  }

  public EventTraceStateDto() {}

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(final String traceId) {
    this.traceId = traceId;
  }

  public List<TracedEventDto> getEventTrace() {
    return eventTrace;
  }

  public void setEventTrace(final List<TracedEventDto> eventTrace) {
    this.eventTrace = eventTrace;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventTraceStateDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $traceId = getTraceId();
    result = result * PRIME + ($traceId == null ? 43 : $traceId.hashCode());
    final Object $eventTrace = getEventTrace();
    result = result * PRIME + ($eventTrace == null ? 43 : $eventTrace.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventTraceStateDto)) {
      return false;
    }
    final EventTraceStateDto other = (EventTraceStateDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$traceId = getTraceId();
    final Object other$traceId = other.getTraceId();
    if (this$traceId == null ? other$traceId != null : !this$traceId.equals(other$traceId)) {
      return false;
    }
    final Object this$eventTrace = getEventTrace();
    final Object other$eventTrace = other.getEventTrace();
    if (this$eventTrace == null
        ? other$eventTrace != null
        : !this$eventTrace.equals(other$eventTrace)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventTraceStateDto(traceId=" + getTraceId() + ", eventTrace=" + getEventTrace() + ")";
  }

  public static EventTraceStateDtoBuilder builder() {
    return new EventTraceStateDtoBuilder();
  }

  public static final class Fields {

    public static final String traceId = "traceId";
    public static final String eventTrace = "eventTrace";
  }

  public static class EventTraceStateDtoBuilder {

    private String traceId;
    private List<TracedEventDto> eventTrace;

    EventTraceStateDtoBuilder() {}

    public EventTraceStateDtoBuilder traceId(final String traceId) {
      this.traceId = traceId;
      return this;
    }

    public EventTraceStateDtoBuilder eventTrace(final List<TracedEventDto> eventTrace) {
      this.eventTrace = eventTrace;
      return this;
    }

    public EventTraceStateDto build() {
      return new EventTraceStateDto(traceId, eventTrace);
    }

    @Override
    public String toString() {
      return "EventTraceStateDto.EventTraceStateDtoBuilder(traceId="
          + traceId
          + ", eventTrace="
          + eventTrace
          + ")";
    }
  }
}
