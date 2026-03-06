/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PRE_FLATTENED_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.PRE_FLATTENED_INDEX;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getPreFlattenedIndexAliasName;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.importing.zeebe.cache.PreFlattenedDTO;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Writer that generates bulk index requests for {@link PreFlattenedDTO} documents so they can be
 * flushed to the {@code PreFlattenedIndex} every second via the sliding window cache scheduler.
 */
@Component
public class PreFlattenedWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(PreFlattenedWriter.class);
  private final IndexRepository indexRepository;

  public PreFlattenedWriter(final IndexRepository indexRepository) {
    this.indexRepository = indexRepository;
  }

  /**
   * Generates a list of {@link ImportRequestDto} (INDEX type) for the given {@link
   * PreFlattenedDTO} documents. Ensures the required per-definition indices and the shared
   * multi-alias exist before returning the requests.
   *
   * @param dtos the pre-flattened DTOs to index
   * @return list of import requests ready for bulk execution
   */
  public List<ImportRequestDto> generatePreFlattenedImports(final List<PreFlattenedDTO> dtos) {
    final String importItemName = "pre-flattened process instances";
    LOG.debug("Creating imports for [{}].", importItemName);

    indexRepository.createMissingIndices(
        PRE_FLATTENED_INDEX,
        Set.of(PRE_FLATTENED_MULTI_ALIAS),
        dtos.stream()
            .map(dto -> dto.getProcessDefinitionId())
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet()));

    return dtos.stream()
        .map(
            dto ->
                ImportRequestDto.builder()
                    .importName(importItemName)
                    .type(RequestType.INDEX)
                    .id(String.valueOf(dto.getProcessInstanceId()))
                    .indexName(getPreFlattenedIndexAliasName(dto.getProcessDefinitionId()))
                    .source(dto)
                    .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                    .build())
        .toList();
  }
}
