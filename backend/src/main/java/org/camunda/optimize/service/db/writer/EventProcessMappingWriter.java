/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import com.google.common.collect.Sets;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.service.db.repository.MappingRepository;
import org.camunda.optimize.service.db.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessMappingWriter {
  private final MappingRepository mappingRepository;

  public IdResponseDto createEventProcessMapping(
      final EventProcessMappingDto eventProcessMappingDto) {
    String id = IdGenerator.getNextId();
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
