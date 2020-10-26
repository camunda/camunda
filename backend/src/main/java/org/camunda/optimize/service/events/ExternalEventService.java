/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.process.EventResponseDto;
import org.camunda.optimize.service.es.reader.ExternalEventReader;
import org.camunda.optimize.service.es.writer.ExternalEventWriter;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Component
public class ExternalEventService implements EventFetcherService<EventResponseDto> {

  private final ExternalEventReader externalEventReader;
  private final ExternalEventWriter eventWriter;

  public void saveEventBatch(final List<EventResponseDto> eventDtos) {
    eventWriter.upsertEvents(eventDtos);
  }

  @Override
  public List<EventResponseDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    return externalEventReader.getEventsIngestedAfter(ingestTimestamp, limit);
  }

  @Override
  public List<EventResponseDto> getEventsIngestedAt(final Long ingestTimestamp) {
    return externalEventReader.getEventsIngestedAt(ingestTimestamp);
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestamps() {
    return externalEventReader.getMinAndMaxIngestedTimestamps();
  }

}
