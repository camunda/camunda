/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.sequence;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventSequenceCountDto implements OptimizeDto {

  public static final String ID_FIELD_SEPARATOR = ":";
  public static final String ID_EVENT_SEPARATOR = "%";

  String id;
  EventTypeDto sourceEvent;
  EventTypeDto targetEvent;
  Long count;

  public String getId() {
    if (id == null) {
      generateIdForEventSequenceCountDto();
    }
    return id;
  }

  public void generateIdForEventSequenceCountDto() {
    if (id == null) {
      id =
          generateIdForEventType(sourceEvent)
              + ID_EVENT_SEPARATOR
              + generateIdForEventType(targetEvent);
    }
  }

  private String generateIdForEventType(final EventTypeDto eventTypeDto) {
    final Optional<EventTypeDto> eventType = Optional.ofNullable(eventTypeDto);
    return String.join(
        ID_FIELD_SEPARATOR,
        eventType.map(EventTypeDto::getGroup).orElse(null),
        eventType.map(EventTypeDto::getSource).orElse(null),
        eventType.map(EventTypeDto::getEventName).orElse(null));
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String sourceEvent = "sourceEvent";
    public static final String targetEvent = "targetEvent";
    public static final String count = "count";
  }
}
