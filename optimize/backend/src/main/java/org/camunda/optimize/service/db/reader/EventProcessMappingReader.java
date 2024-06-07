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
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.service.db.repository.EventRepository;
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
