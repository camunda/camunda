/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.FLAT_INCIDENT_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.FLAT_INCIDENT_INDEX;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getFlatIncidentIndexAliasName;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.persistence.incident.FlatIncidentDto;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.importing.zeebe.cache.OrdinalCache;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class IncidentWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(IncidentWriter.class);
  private final IndexRepository indexRepository;
  private final OrdinalCache ordinalCache;

  public IncidentWriter(final IndexRepository indexRepository, final OrdinalCache ordinalCache) {
    this.indexRepository = indexRepository;
    this.ordinalCache = ordinalCache;
  }

  public List<ImportRequestDto> generateFlatIncidentImports(final List<FlatIncidentDto> incidents) {
    final String importItemName = "flat incidents";
    LOG.debug("Creating imports for [{}].", importItemName);

    // Compute combined index keys (processDefinitionKey-ordinalTick) for index creation.
    final Set<String> combinedKeys =
        incidents.stream()
            .map(i -> ordinalCache.combinedIndexKey(i.getProcessDefinitionKey(), i.getOrdinal()))
            .collect(Collectors.toSet());
    indexRepository.createMissingIndices(
        FLAT_INCIDENT_INDEX, Set.of(FLAT_INCIDENT_MULTI_ALIAS), combinedKeys);

    return incidents.stream()
        .map(
            incident ->
                ImportRequestDto.builder()
                    .importName(importItemName)
                    .type(RequestType.INDEX)
                    .id(incident.getId())
                    .indexName(
                        getFlatIncidentIndexAliasName(
                            incident.getProcessDefinitionKey(),
                            ordinalCache.getTickString(incident.getOrdinal())))
                    .source(incident)
                    .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                    .build())
        .toList();
  }
}
