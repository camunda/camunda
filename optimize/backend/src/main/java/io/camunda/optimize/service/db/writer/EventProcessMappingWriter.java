/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import com.google.common.collect.Sets;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.service.db.repository.MappingRepository;
import io.camunda.optimize.service.db.schema.index.events.EventProcessMappingIndex;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.IdGenerator;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class EventProcessMappingWriter {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(EventProcessMappingWriter.class);
  private final MappingRepository mappingRepository;

  public EventProcessMappingWriter(final MappingRepository mappingRepository) {
    this.mappingRepository = mappingRepository;
  }

  public IdResponseDto createEventProcessMapping(
      final EventProcessMappingDto eventProcessMappingDto) {
    final String id = IdGenerator.getNextId();
    eventProcessMappingDto.setId(id);
    eventProcessMappingDto.setLastModified(LocalDateUtil.getCurrentDateTime());
    log.debug("Writing event-based process [{}] to Database", id);
    return mappingRepository.createEventProcessMapping(eventProcessMappingDto);
  }

  public void updateEventProcessMapping(final EventProcessMappingDto eventProcessMappingDto) {
    mappingRepository.updateEventProcessMappingWithScript(
        eventProcessMappingDto,
        Sets.newHashSet(
            EventProcessMappingIndex.NAME,
            EventProcessMappingIndex.XML,
            EventProcessMappingIndex.MAPPINGS,
            EventProcessMappingIndex.LAST_MODIFIED,
            EventProcessMappingIndex.LAST_MODIFIER,
            EventProcessMappingIndex.EVENT_SOURCES));
  }

  public void updateRoles(final EventProcessMappingDto eventProcessMappingDto) {
    mappingRepository.updateEventProcessMappingWithScript(
        eventProcessMappingDto, Sets.newHashSet(EventProcessMappingIndex.ROLES));
  }

  public void deleteEventProcessMappings(final List<String> eventProcessMappingIds) {
    mappingRepository.deleteEventProcessMappings(eventProcessMappingIds);
  }
}
