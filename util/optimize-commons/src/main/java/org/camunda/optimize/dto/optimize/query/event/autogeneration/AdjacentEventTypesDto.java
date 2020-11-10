/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event.autogeneration;

import lombok.Builder;
import lombok.Data;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class AdjacentEventTypesDto {
  @Builder.Default
  private List<EventTypeDto> precedingEvents = new ArrayList<>();
  @Builder.Default
  private List<EventTypeDto> succeedingEvents = new ArrayList<>();
}
