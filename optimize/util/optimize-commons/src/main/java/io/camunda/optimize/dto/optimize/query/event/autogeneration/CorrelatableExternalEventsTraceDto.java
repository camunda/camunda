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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class CorrelatableExternalEventsTraceDto extends CorrelatableInstanceDto {
  private String tracingId;

  private CorrelatableExternalEventsTraceDto(
      final String tracingId, final OffsetDateTime startDate) {
    super(startDate);
    this.tracingId = tracingId;
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
