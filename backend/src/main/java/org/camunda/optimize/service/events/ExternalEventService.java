/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.service.es.reader.ExternalEventReader;
import org.camunda.optimize.service.es.writer.ExternalEventWriter;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
public class ExternalEventService implements EventFetcherService {

  private final ExternalEventReader eventReader;
  private final ExternalEventWriter eventWriter;

  public void saveEventBatch(final List<EventDto> eventDtos) {
    eventWriter.upsertEvents(eventDtos);
  }

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    return eventReader.getEventsIngestedAfter(ingestTimestamp, limit);
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    return eventReader.getEventsIngestedAt(ingestTimestamp);
  }

  public Long countEventsIngestedBeforeAndAtIngestTimestamp(final Long ingestTimestamp) {
    return eventReader.countEventsIngestedBeforeAndAtIngestTimestamp(ingestTimestamp);
  }
}
