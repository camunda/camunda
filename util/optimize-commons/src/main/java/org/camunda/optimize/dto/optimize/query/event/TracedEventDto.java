/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

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

  public static TracedEventDto fromEventDto(EventDto eventDto) {
    return TracedEventDto.builder()
      .eventId(eventDto.getId())
      .timestamp(eventDto.getTimestamp())
      .group(eventDto.getGroup())
      .source(eventDto.getSource())
      .eventName(eventDto.getEventName())
      .build();
  }

}
