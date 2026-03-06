/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.FLAT_VARIABLE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.FLAT_VARIABLE_INDEX;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getFlatVariableIndexAliasName;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.query.variable.FlatVariableDto;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.importing.zeebe.cache.OrdinalCache;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class VariableWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(VariableWriter.class);
  private final IndexRepository indexRepository;
  private final OrdinalCache ordinalCache;

  public VariableWriter(final IndexRepository indexRepository, final OrdinalCache ordinalCache) {
    this.indexRepository = indexRepository;
    this.ordinalCache = ordinalCache;
  }

  public List<ImportRequestDto> generateFlatVariableImports(final List<FlatVariableDto> variables) {
    final String importItemName = "flat variables";
    LOG.debug("Creating imports for [{}].", importItemName);

    final Set<String> combinedKeys =
        variables.stream()
            .map(v -> ordinalCache.combinedIndexKey(v.getProcessDefinitionKey(), v.getOrdinal()))
            .collect(Collectors.toSet());
    indexRepository.createMissingIndices(
        FLAT_VARIABLE_INDEX, Set.of(FLAT_VARIABLE_MULTI_ALIAS), combinedKeys);

    return variables.stream()
        .map(
            variable ->
                ImportRequestDto.builder()
                    .importName(importItemName)
                    .type(RequestType.INDEX)
                    .id(variable.getId())
                    .indexName(
                        getFlatVariableIndexAliasName(
                            variable.getProcessDefinitionKey(),
                            ordinalCache.getTickString(variable.getOrdinal())))
                    .source(variable)
                    .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                    .build())
        .toList();
  }
}
