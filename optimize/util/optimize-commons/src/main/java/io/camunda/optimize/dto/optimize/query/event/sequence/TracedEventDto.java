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
import lombok.experimental.FieldNameConstants;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class TracedEventDto implements OptimizeDto {

  private String eventId;
  private String group;
  private String source;
  private String eventName;
  private Long timestamp;
  private Long orderCounter;

  public static TracedEventDto fromEventDto(EventDto eventDto) {
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
}
