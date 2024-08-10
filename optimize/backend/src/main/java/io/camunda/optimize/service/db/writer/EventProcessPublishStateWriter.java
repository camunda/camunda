/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME;
import static io.camunda.optimize.service.db.writer.DatabaseWriterUtil.createFieldUpdateScriptParams;
import static io.camunda.optimize.service.db.writer.DatabaseWriterUtil.createScriptData;
import static io.camunda.optimize.service.db.writer.DatabaseWriterUtil.createUpdateFieldsScript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.dto.optimize.query.event.process.db.DbEventProcessPublishStateDto;
import io.camunda.optimize.service.db.repository.EventRepository;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.events.EventProcessPublishStateIndex;
import io.camunda.optimize.service.util.IdGenerator;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessPublishStateWriter {
  private EventRepository eventRepository;
  private final ObjectMapper objectMapper;

  public IdResponseDto createEventProcessPublishState(
      final EventProcessPublishStateDto eventProcessPublishStateDto) {
    String id = IdGenerator.getNextId();
    eventProcessPublishStateDto.setId(id);
    log.debug("Writing event process publish state [{}] to database", id);
    return eventRepository.createEventProcessPublishState(eventProcessPublishStateDto);
  }

  public void updateEventProcessPublishState(
      final EventProcessPublishStateDto eventProcessPublishStateDto) {
    String id = eventProcessPublishStateDto.getId();
    log.debug("Updating event process publish state [{}] in database.", id);
    Map<String, Object> parameterMap =
        createFieldUpdateScriptParams(
            Sets.newHashSet(
                EventProcessPublishStateIndex.EVENT_IMPORT_SOURCES,
                EventProcessPublishStateIndex.PUBLISH_PROGRESS,
                EventProcessPublishStateIndex.STATE),
            DbEventProcessPublishStateDto.fromEventProcessPublishStateDto(
                eventProcessPublishStateDto),
            objectMapper);
    ScriptData scriptData =
        createScriptData(
            createUpdateFieldsScript(
                ImmutableSet.of(
                    DbEventProcessPublishStateDto.Fields.eventImportSources,
                    DbEventProcessPublishStateDto.Fields.publishProgress,
                    DbEventProcessPublishStateDto.Fields.state)),
            parameterMap,
            objectMapper);
    eventRepository.updateEntry(EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME, id, scriptData);
  }

  public boolean markAsDeletedAllEventProcessPublishStatesForEventProcessMappingId(
      final String eventProcessMappingId) {
    final String updateItem =
        String.format(
            "event process publish state with %s [%s]",
            EventProcessPublishStateIndex.PROCESS_MAPPING_ID, eventProcessMappingId);
    log.debug("Flagging {} as deleted.", updateItem);
    ScriptData scriptData =
        createScriptData(
            createUpdateFieldsScript(ImmutableSet.of(DbEventProcessPublishStateDto.Fields.deleted)),
            ImmutableMap.of(DbEventProcessPublishStateDto.Fields.deleted, true),
            objectMapper);

    return eventRepository.markAsDeletedAllEventProcessPublishStatesForEventProcessMappingId(
        eventProcessMappingId,
        updateItem,
        scriptData,
        EventProcessPublishStateIndex.PROCESS_MAPPING_ID,
        EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME);
  }

  public void markAsDeletedPublishStatesForEventProcessMappingIdExcludingPublishStateId(
      final String eventProcessMappingId, final String publishStateIdToExclude) {
    final String updateItem =
        String.format(
            "event process publish state with %s [%s]",
            EventProcessPublishStateIndex.PROCESS_MAPPING_ID, eventProcessMappingId);
    log.debug(
        "Flagging {} as deleted, except for process publish state with ID [{}].",
        updateItem,
        publishStateIdToExclude);
    ScriptData scriptData =
        createScriptData(
            createUpdateFieldsScript(ImmutableSet.of(DbEventProcessPublishStateDto.Fields.deleted)),
            ImmutableMap.of(DbEventProcessPublishStateDto.Fields.deleted, true),
            objectMapper);
    eventRepository.markAsDeletedPublishStatesForEventProcessMappingIdExcludingPublishStateId(
        eventProcessMappingId,
        updateItem,
        scriptData,
        EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME,
        publishStateIdToExclude);
  }
}
