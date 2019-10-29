/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventWriter;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
public class EventService {
  private final EventWriter eventWriter;

  public void saveEvent(final EventDto eventDto) {
    eventWriter.upsertEvent(eventDto);
  }

  public void saveEventBatch(final List<EventDto> eventDtos) {
    eventWriter.upsertEvents(eventDtos);
  }

}
