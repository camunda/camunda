/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.sequence;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TracedEventDto implements OptimizeDto {

  private String eventId;
  private String group;
  private String source;
  private String eventName;
  private Long timestamp;
  private Long orderCounter;

  public static TracedEventDto fromEventDto(final EventDto eventDto) {
    return TracedEventDto.builder()
        .eventId(eventDto.getId())
        .timestamp(eventDto.getTimestamp())
        .group(eventDto.getGroup())
        .source(eventDto.getSource())
        .eventName(eventDto.getEventName())
        .orderCounter(
            eventDto instanceof OrderedEventDto
                ? ((OrderedEventDto) eventDto).getOrderCounter()
                : null)
        .build();
  }

  public static final class Fields {

    public static final String eventId = "eventId";
    public static final String group = "group";
    public static final String source = "source";
    public static final String eventName = "eventName";
    public static final String timestamp = "timestamp";
    public static final String orderCounter = "orderCounter";
  }
}
