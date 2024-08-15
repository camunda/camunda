/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.autogeneration;

import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import io.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.TracedEventDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;

public class CorrelatableExternalEventsTraceDto extends CorrelatableInstanceDto {

  private String tracingId;

  private CorrelatableExternalEventsTraceDto(
      final String tracingId, final OffsetDateTime startDate) {
    super(startDate);
    this.tracingId = tracingId;
  }

  public CorrelatableExternalEventsTraceDto(final String tracingId) {
    this.tracingId = tracingId;
  }

  public CorrelatableExternalEventsTraceDto() {
  }

  public static CorrelatableExternalEventsTraceDto fromEventTraceState(
      final EventTraceStateDto trace) {
    final OffsetDateTime startDate =
        trace.getEventTrace().stream()
            .min(Comparator.comparing(TracedEventDto::getTimestamp))
            .map(
                tracedEventDto ->
                    OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(tracedEventDto.getTimestamp()),
                        ZoneId.systemDefault()))
            .orElseThrow(
                () ->
                    new OptimizeRuntimeException(
                        "There was a problem converting an external event trace as no events exist"));
    return new CorrelatableExternalEventsTraceDto(trace.getTraceId(), startDate);
  }

  public String getTracingId() {
    return tracingId;
  }

  public void setTracingId(final String tracingId) {
    this.tracingId = tracingId;
  }

  @Override
  public String toString() {
    return "CorrelatableExternalEventsTraceDto(tracingId=" + getTracingId() + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CorrelatableExternalEventsTraceDto)) {
      return false;
    }
    final CorrelatableExternalEventsTraceDto other = (CorrelatableExternalEventsTraceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$tracingId = getTracingId();
    final Object other$tracingId = other.getTracingId();
    if (this$tracingId == null ? other$tracingId != null
        : !this$tracingId.equals(other$tracingId)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof CorrelatableExternalEventsTraceDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $tracingId = getTracingId();
    result = result * PRIME + ($tracingId == null ? 43 : $tracingId.hashCode());
    return result;
  }

  @Override
  public String getSourceIdentifier() {
    // Autogeneration is only supported for external groups when they are a single bucket of events
    // without group
    return EventSourceType.EXTERNAL.getId() + ":" + "optimize_allExternalEventGroups";
  }

  @Override
  public String getCorrelationValueForEventSource(
      final EventSourceEntryDto<?> eventSourceEntryDto) {
    if (eventSourceEntryDto instanceof ExternalEventSourceEntryDto) {
      return tracingId;
    }
    throw new IllegalArgumentException("Cannot get correlation value from non-external sources");
  }
}
