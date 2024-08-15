/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.db;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DbEventMappingDto implements OptimizeDto {

  String flowNodeId;
  EventTypeDto start;
  EventTypeDto end;

  public static DbEventMappingDto fromEventMappingDto(
      final String flowNodeId, final EventMappingDto eventMappingDto) {
    return DbEventMappingDto.builder()
        .flowNodeId(flowNodeId)
        .start(eventMappingDto.getStart())
        .end(eventMappingDto.getEnd())
        .build();
  }

  public static final class Fields {

    public static final String flowNodeId = "flowNodeId";
    public static final String start = "start";
    public static final String end = "end";
  }
}
