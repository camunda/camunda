/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.ORDINAL_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ORDINAL_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.ORDINAL_INDEX;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.OrdinalDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.service.db.repository.IndexRepository;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Writer that generates bulk index requests for {@link OrdinalDto} documents so they can be flushed
 * to the {@code ordinal} index.
 */
@Component
public class OrdinalWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(OrdinalWriter.class);
  private final IndexRepository indexRepository;

  public OrdinalWriter(final IndexRepository indexRepository) {
    this.indexRepository = indexRepository;
  }

  /**
   * Generates a list of {@link ImportRequestDto} (INDEX type) for the given {@link OrdinalDto}
   * documents. Ensures the ordinal index exists before returning the requests.
   *
   * @param ordinals the ordinal DTOs to index
   * @return list of import requests ready for bulk execution
   */
  public List<ImportRequestDto> generateOrdinalImports(final List<OrdinalDto> ordinals) {
    final String importItemName = "ordinals";
    LOG.debug("Creating imports for [{}].", importItemName);

    // Ensure the singleton ordinal index exists (it is not keyed by definition, so pass empty)
    indexRepository.createMissingIndices(
        ORDINAL_INDEX, Set.of(ORDINAL_MULTI_ALIAS), Set.of(ORDINAL_INDEX_NAME));

    return ordinals.stream()
        .map(
            dto ->
                ImportRequestDto.builder()
                    .importName(importItemName)
                    .type(RequestType.INDEX)
                    .id(String.valueOf(dto.getOrdinal()))
                    .indexName(ORDINAL_MULTI_ALIAS)
                    .source(dto)
                    .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                    .build())
        .toList();
  }
}
