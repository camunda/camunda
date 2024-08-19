/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.service.db.repository.EventRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class EventProcessPublishStateReader {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(EventProcessPublishStateReader.class);
  private final EventRepository eventRepository;

  public EventProcessPublishStateReader(final EventRepository eventRepository) {
    this.eventRepository = eventRepository;
  }

  public Optional<EventProcessPublishStateDto> getEventProcessPublishStateByEventProcessId(
      final String eventProcessMappingId) {
    log.debug(
        "Fetching event process publish state with eventProcessMappingId [{}].",
        eventProcessMappingId);
    return eventRepository.getEventProcessPublishStateByEventProcessId(eventProcessMappingId);
  }

  public List<EventProcessPublishStateDto> getAllEventProcessPublishStates() {
    return getAllEventProcessPublishStatesWithDeletedState(false);
  }

  public List<EventProcessPublishStateDto> getAllEventProcessPublishStatesWithDeletedState(
      final boolean deleted) {
    log.debug(
        "Fetching all available event process publish states with deleted state [{}].", deleted);
    return eventRepository.getAllEventProcessPublishStatesWithDeletedState(deleted);
  }
}
