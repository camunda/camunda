/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.service.db.repository.EventRepository;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessPublishStateReader {

  private final EventRepository eventRepository;

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
