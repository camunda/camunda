/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import io.camunda.optimize.service.db.repository.EventRepository;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessMappingReader {
  EventRepository eventRepository;

  public Optional<EventProcessMappingDto> getEventProcessMapping(
      final String eventProcessMappingId) {
    log.debug("Fetching event-based process with id [{}].", eventProcessMappingId);
    return eventRepository.getEventProcessMapping(eventProcessMappingId);
  }

  public List<EventProcessMappingDto> getAllEventProcessMappingsOmitXml() {
    log.debug("Fetching all available event-based processes.");
    return eventRepository.getAllEventProcessMappingsOmitXml();
  }

  public List<EventProcessRoleRequestDto<IdentityDto>> getEventProcessRoles(
      final String eventProcessMappingId) {
    log.debug(
        "Fetching event process roles for event process mapping id [{}].", eventProcessMappingId);
    return eventRepository.getEventProcessRoles(eventProcessMappingId);
  }
}
