/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event.autogeneration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.TracedEventDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;

@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class CorrelatableExternalEventsTraceDto extends CorrelatableInstanceDto {
  private String tracingId;

  private CorrelatableExternalEventsTraceDto(final String tracingId, final OffsetDateTime startDate) {
    super(startDate);
    this.tracingId = tracingId;
  }

  public static CorrelatableExternalEventsTraceDto fromEventTraceState(final EventTraceStateDto trace) {
    final OffsetDateTime startDate = trace.getEventTrace()
      .stream()
      .min(Comparator.comparing(TracedEventDto::getTimestamp))
      .map(tracedEventDto -> OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(tracedEventDto.getTimestamp()), ZoneId.systemDefault()))
      .orElseThrow(() -> new OptimizeRuntimeException(
        "There was a problem converting an external event trace as no events exist"));
    return new CorrelatableExternalEventsTraceDto(trace.getTraceId(), startDate);
  }

  @Override
  public String getSourceIdentifier() {
    // TODO Autogeneration will be supported for multiple groups in OPT-4541
    return EventSourceType.EXTERNAL.getId() + ":" + "allExternalEventGroups";
  }

  @Override
  public String getCorrelationValueForEventSource(final EventSourceEntryDto<?> eventSourceEntryDto) {
    if (eventSourceEntryDto instanceof ExternalEventSourceEntryDto) {
      return tracingId;
    }
    throw new OptimizeRuntimeException("Cannot get correlation value from non-external sources");
  }

}
