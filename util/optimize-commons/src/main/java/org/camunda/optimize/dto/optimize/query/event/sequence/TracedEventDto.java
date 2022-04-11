/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.sequence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;

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
      .orderCounter(eventDto instanceof OrderedEventDto ? ((OrderedEventDto) eventDto).getOrderCounter() : null)
      .build();
  }

}
