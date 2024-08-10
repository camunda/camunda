/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event;

import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldNameConstants
public class DeletableEventDto {

  private String id;
  private String traceId;
  private String group;
  private String source;
  private String eventName;
  private Instant timestamp;

  public static DeletableEventDto from(final EventDto eventDto) {
    return new DeletableEventDto(
        eventDto.getId(),
        eventDto.getTraceId(),
        eventDto.getGroup(),
        eventDto.getSource(),
        eventDto.getEventName(),
        Instant.ofEpochMilli(eventDto.getTimestamp()));
  }
}
