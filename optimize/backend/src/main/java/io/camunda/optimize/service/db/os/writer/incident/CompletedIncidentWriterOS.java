/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.repository.IndexRepository;
import io.camunda.optimize.service.db.writer.incident.CompletedIncidentWriter;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class CompletedIncidentWriterOS extends AbstractIncidentWriterOS
    implements CompletedIncidentWriter {

  public CompletedIncidentWriterOS(
      final IndexRepository indexRepository, final ObjectMapper objectMapper) {
    super(indexRepository, objectMapper);
  }
}
