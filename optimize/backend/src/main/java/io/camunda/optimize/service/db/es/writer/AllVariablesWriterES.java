/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.ALL_VARIABLES_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.importing.AllVariablesDto;
import io.camunda.optimize.service.db.writer.AllVariablesWriter;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AllVariablesWriterES implements AllVariablesWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AllVariablesWriterES.class);

  private final ObjectMapper objectMapper;

  public AllVariablesWriterES(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public List<ImportRequestDto> generateImports(final List<AllVariablesDto> documents) {
    LOG.debug("Creating all-variables imports for {} documents.", documents.size());
    return documents.stream()
        .map(
            doc ->
                ImportRequestDto.builder()
                    .importName("all variables")
                    .type(RequestType.UPDATE)
                    .id(doc.getVariableKey())
                    .indexName(ALL_VARIABLES_INDEX_NAME)
                    .source(doc)
                    .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                    .build())
        .collect(Collectors.toList());
  }
}
