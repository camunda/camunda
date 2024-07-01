/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import io.camunda.optimize.service.db.repository.EventRepository;
import io.camunda.optimize.service.db.repository.Repository;
import io.camunda.optimize.service.db.repository.TaskRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Slf4j
@Component
public class ExternalEventWriter {

  private EventRepository eventRepository;
  private final TaskRepository taskRepository;
  private final Repository repository;

  public void upsertEvents(final List<EventDto> eventDtos) {
    log.debug("Writing [{}] events to database", eventDtos.size());
    eventRepository.upsertEvents(eventDtos);
  }

  public void deleteEventsOlderThan(final OffsetDateTime timestamp) {
    final String deletedItemIdentifier =
        String.format("external events with timestamp older than %s", timestamp);
    log.info("Deleting {}", deletedItemIdentifier);
    taskRepository.executeWithTaskMonitoring(
        repository.getDeleteByQueryActionName(),
        () -> eventRepository.deleteEventsOlderThan(timestamp, deletedItemIdentifier),
        log);
  }

  public void deleteEventsWithIdsIn(final List<String> eventIdsToDelete) {
    final String deletedItemIdentifier =
        String.format("external events with ID from list of size %s", eventIdsToDelete.size());
    log.info("Deleting {} events by ID.", eventIdsToDelete.size());
    eventRepository.deleteEventsWithIdsIn(eventIdsToDelete, deletedItemIdentifier);
  }
}
