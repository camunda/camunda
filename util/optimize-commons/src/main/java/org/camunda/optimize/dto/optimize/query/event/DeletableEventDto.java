/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;

import java.time.Instant;

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
      Instant.ofEpochMilli(eventDto.getTimestamp())
    );
  }

}
