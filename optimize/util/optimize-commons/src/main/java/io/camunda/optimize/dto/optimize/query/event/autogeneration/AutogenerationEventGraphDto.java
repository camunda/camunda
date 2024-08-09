/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.autogeneration;

import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AutogenerationEventGraphDto {
  private List<EventTypeDto> startEvents;
  private List<EventTypeDto> endEvents;
  private Map<EventTypeDto, AutogenerationAdjacentEventTypesDto> adjacentEventTypesDtoMap;
}
