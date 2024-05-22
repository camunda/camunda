/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.events;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventGroupRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.Page;
import org.camunda.optimize.service.db.es.schema.index.events.EventProcessInstanceIndexES;
import org.camunda.optimize.service.db.events.EventFetcherService;
import org.camunda.optimize.service.db.reader.ExternalEventReader;
import org.camunda.optimize.service.db.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.db.writer.ExternalEventWriter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
public class ExternalEventService implements EventFetcherService<EventDto> {

  private final ExternalEventReader externalEventReader;
  private final ExternalEventWriter externalEventWriter;
  private final EventProcessInstanceWriter eventInstanceWriter;

  public Page<DeletableEventDto> getEventsForRequest(
      final EventSearchRequestDto eventSearchRequestDto) {
    return externalEventReader.getEventsForRequest(eventSearchRequestDto);
  }

  public List<String> getEventGroups(final EventGroupRequestDto eventGroupRequestDto) {
    return externalEventReader.getEventGroups(eventGroupRequestDto);
  }

  public void saveEventBatch(final List<EventDto> eventDtos) {
    final Long rightNow = LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli();
    // all events of a batch share the same ingestion timestamp as this is the point in time they
    // got ingested
    for (EventDto eventDto : eventDtos) {
      eventDto.setIngestionTimestamp(rightNow);
    }
    externalEventWriter.upsertEvents(eventDtos);
  }

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    return externalEventReader.getEventsIngestedAfter(ingestTimestamp, limit);
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    return externalEventReader.getEventsIngestedAt(ingestTimestamp);
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForAllEvents() {
    return externalEventReader.getMinAndMaxIngestedTimestamps();
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForGroups(final List<String> eventGroups) {
    return externalEventReader.getMinAndMaxIngestedTimestampsForGroups(eventGroups);
  }

  public void deleteEvents(final List<String> eventIdsToDelete) {
    // it's ok to use the ES index because we are not actually wanting to create an index,
    // but instead we're just misusing the constructor in order to get the fully qualified index
    // name
    final String index = new EventProcessInstanceIndexES("*").getIndexName();
    eventInstanceWriter.deleteEventsWithIdsInFromAllInstances(index, eventIdsToDelete);
    externalEventWriter.deleteEventsWithIdsIn(eventIdsToDelete);
  }
}
