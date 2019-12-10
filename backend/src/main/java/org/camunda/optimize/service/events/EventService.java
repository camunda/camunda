/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.service.es.reader.EventReader;
import org.camunda.optimize.service.es.writer.EventWriter;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
public class EventService {

  private final EventReader eventReader;
  private final EventWriter eventWriter;

  public void saveEvent(final EventDto eventDto) {
    eventWriter.upsertEvent(eventDto);
  }

  public void saveEventBatch(final List<EventDto> eventDtos) {
    eventWriter.upsertEvents(eventDtos);
  }

  public List<EventCountDto> getEventCounts(EventCountRequestDto eventCountRequestDto) {
    return eventReader.getEventCounts(eventCountRequestDto);
  }

  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    return eventReader.getEventsIngestedAfter(ingestTimestamp, limit);
  }

  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    return eventReader.getEventsIngestedAt(ingestTimestamp);
  }

  public Long countEventsIngestedBeforeAndAtIngestTimestamp(final Long ingestTimestamp) {
    return eventReader.countEventsIngestedBeforeAndAtIngestTimestamp(ingestTimestamp);
  }

}
