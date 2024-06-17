/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import io.camunda.optimize.dto.optimize.query.event.EventGroupRequestDto;
import io.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import io.camunda.optimize.dto.optimize.rest.Page;
import io.camunda.optimize.service.db.repository.EventRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class ExternalEventReader {

  private EventRepository eventRepository;

  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    log.debug("Fetching events that where ingested after {}", ingestTimestamp);
    return eventRepository.getEventsIngestedAfter(ingestTimestamp, limit);
  }

  public List<EventDto> getEventsIngestedAfterForGroups(
      final Long ingestTimestamp, final int limit, final List<String> groups) {
    log.debug(
        "Fetching events that where ingested after {} for groups {}", ingestTimestamp, groups);
    return eventRepository.getEventsIngestedAfterForGroups(ingestTimestamp, limit, groups);
  }

  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    log.debug("Fetching events that where ingested at {}", ingestTimestamp);
    return eventRepository.getEventsIngestedAt(ingestTimestamp);
  }

  public List<EventDto> getEventsIngestedAtForGroups(
      final Long ingestTimestamp, final List<String> groups) {
    log.debug("Fetching events that where ingested at {} for groups {}", ingestTimestamp, groups);
    return eventRepository.getEventsIngestedAtForGroups(ingestTimestamp, groups);
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestamps() {
    log.debug("Fetching min and max timestamp for ingested external events");
    return eventRepository.getMinAndMaxIngestedTimestamps();
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>>
      getMinAndMaxIngestedTimestampsForGroups(final List<String> groups) {
    log.debug("Fetching min and max timestamp for ingested external events in groups: {}", groups);
    return eventRepository.getMinAndMaxIngestedTimestampsForGroups(groups);
  }

  public Page<DeletableEventDto> getEventsForRequest(
      final EventSearchRequestDto eventSearchRequestDto) {
    log.debug("Fetching events using search criteria {}", eventSearchRequestDto);
    return eventRepository.getEventsForRequest(eventSearchRequestDto);
  }

  public List<String> getEventGroups(final EventGroupRequestDto eventGroupRequestDto) {
    log.debug("Fetching event groups using search criteria {}", eventGroupRequestDto);
    return eventRepository.getEventGroups(eventGroupRequestDto);
  }
}
