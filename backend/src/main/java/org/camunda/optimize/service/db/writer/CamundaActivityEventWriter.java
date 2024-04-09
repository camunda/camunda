/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import static org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.ACTIVITY_EVENT_INDEX;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.service.db.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.db.repository.EventRepository;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.db.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.util.IdGenerator;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class CamundaActivityEventWriter {
  private final IndexRepository indexRepository;
  private final EventRepository eventRepository;
  private final CamundaActivityEventReader camundaActivityEventReader;

  public List<ImportRequestDto> generateImportRequests(
      final List<CamundaActivityEventDto> camundaActivityEvents) {
    final String importItemName = "camunda activity events";
    log.debug("Creating imports for {} [{}].", camundaActivityEvents.size(), importItemName);

    final Set<String> processDefinitionKeysInBatch =
        camundaActivityEvents.stream()
            .map(CamundaActivityEventDto::getProcessDefinitionKey)
            .collect(Collectors.toSet());

    createMissingActivityIndicesForProcessDefinitions(processDefinitionKeysInBatch);

    return camundaActivityEvents.stream()
        .map(entry -> createIndexRequestForActivityEvent(entry, importItemName))
        .toList();
  }

  public void deleteByProcessInstanceIds(
      final String definitionKey, final List<String> processInstanceIds) {
    log.debug(
        "Deleting camunda activity events for [{}] processInstanceIds", processInstanceIds.size());
    eventRepository.deleteByProcessInstanceIds(definitionKey, processInstanceIds);
  }

  private ImportRequestDto createIndexRequestForActivityEvent(
      final CamundaActivityEventDto camundaActivityEventDto, final String importName) {
    return ImportRequestDto.builder()
        .indexName(
            CamundaActivityEventIndex.constructIndexName(
                camundaActivityEventDto.getProcessDefinitionKey()))
        .id(IdGenerator.getNextId())
        .type(RequestType.INDEX)
        .source(camundaActivityEventDto)
        .importName(importName)
        .build();
  }

  private void createMissingActivityIndicesForProcessDefinitions(
      final Set<String> processDefinitionKeys) {
    final Set<String> currentProcessDefinitions =
        camundaActivityEventReader.getIndexSuffixesForCurrentActivityIndices();
    processDefinitionKeys.removeAll(currentProcessDefinitions);
    indexRepository.createMissingIndices(
        ACTIVITY_EVENT_INDEX, Collections.emptySet(), processDefinitionKeys);
  }
}
