/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.autogeneration;

import lombok.Builder;
import lombok.Getter;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class AutogenerationEventGraphDto {
  private List<EventTypeDto> startEvents;
  private List<EventTypeDto> endEvents;
  private Map<EventTypeDto, AutogenerationAdjacentEventTypesDto> adjacentEventTypesDtoMap;
}
