/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.ALL_VARIABLES_INDEX_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.importing.AllVariablesDto;
import io.camunda.optimize.service.db.writer.AllVariablesWriter;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class AllVariablesWriterOS implements AllVariablesWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AllVariablesWriterOS.class);

  private final ObjectMapper objectMapper;

  public AllVariablesWriterOS(final ObjectMapper objectMapper) {
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
                    .type(RequestType.INDEX)
                    .id(doc.getVariableKey())
                    .indexName(ALL_VARIABLES_INDEX_NAME)
                    .source(doc)
                    .build())
        .collect(Collectors.toList());
  }
}
