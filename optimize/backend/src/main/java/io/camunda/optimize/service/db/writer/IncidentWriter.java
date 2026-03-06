/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.FLAT_INCIDENT_INDEX;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getFlatIncidentIndexAliasName;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.persistence.incident.FlatIncidentDto;
import io.camunda.optimize.service.db.repository.IndexRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class IncidentWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(IncidentWriter.class);
  private final IndexRepository indexRepository;

  public IncidentWriter(final IndexRepository indexRepository) {
    this.indexRepository = indexRepository;
  }

  public List<ImportRequestDto> generateFlatIncidentImports(
      final List<ProcessInstanceDto> processInstances) {
    final String importItemName = "flat incidents";
    LOG.debug("Creating imports for [{}].", importItemName);
    indexRepository.createMissingIndices(
        FLAT_INCIDENT_INDEX,
        Set.of(PROCESS_INSTANCE_MULTI_ALIAS),
        processInstances.stream()
            .map(ProcessInstanceDto::getProcessDefinitionKey)
            .collect(Collectors.toSet()));

    return processInstances.stream()
        .flatMap(
            instance ->
                instance.getIncidents().stream()
                    .map(
                        incident ->
                            ImportRequestDto.builder()
                                .importName(importItemName)
                                .type(RequestType.INDEX)
                                .id(incident.getId())
                                .indexName(
                                    getFlatIncidentIndexAliasName(
                                        instance.getProcessDefinitionKey()))
                                .source(
                                    FlatIncidentDto.fromProcessInstanceAndIncident(
                                        instance.getProcessDefinitionKey(),
                                        instance.getProcessDefinitionVersion(),
                                        instance.getProcessDefinitionId(),
                                        incident))
                                .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                                .build()))
        .toList();
  }
}
