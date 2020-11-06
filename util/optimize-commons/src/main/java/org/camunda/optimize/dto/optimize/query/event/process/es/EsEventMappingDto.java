/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event.process.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class EsEventMappingDto implements OptimizeDto {

  String flowNodeId;
  EventTypeDto start;
  EventTypeDto end;

  public static EsEventMappingDto fromEventMappingDto(final String flowNodeId,
                                                      final EventMappingDto eventMappingDto) {
    return EsEventMappingDto.builder()
      .flowNodeId(flowNodeId)
      .start(eventMappingDto.getStart())
      .end(eventMappingDto.getEnd())
      .build();
  }

}
