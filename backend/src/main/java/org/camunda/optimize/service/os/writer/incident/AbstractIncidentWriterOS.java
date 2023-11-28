/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.service.db.writer.incident.AbstractIncidentWriter;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.os.writer.AbstractProcessInstanceDataWriterOS;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Conditional(OpenSearchCondition.class)
public abstract class AbstractIncidentWriterOS extends AbstractProcessInstanceDataWriterOS<IncidentDto> implements AbstractIncidentWriter {

  private final ObjectMapper objectMapper;

  protected AbstractIncidentWriterOS(final OptimizeOpenSearchClient osClient,
                                     final OpenSearchSchemaManager openSearchSchemaManager,
                                     final ObjectMapper objectMapper) {
    super(osClient, openSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

  @Override
  public List<ImportRequestDto> generateIncidentImports(List<IncidentDto> incidents) {
    //todo will be handled in the OPT-7376
    return new ArrayList<>();
  }

  protected abstract String createInlineUpdateScript();

}
