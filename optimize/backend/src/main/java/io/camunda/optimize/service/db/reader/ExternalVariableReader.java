/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.service.db.repository.VariableRepository;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class ExternalVariableReader {
  VariableRepository variableRepository;

  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAfter(
      final Long ingestTimestamp, final int limit) {
    log.debug(
        "Fetching variables that where ingested after {}", Instant.ofEpochMilli(ingestTimestamp));

    return variableRepository.getVariableUpdatesIngestedAfter(ingestTimestamp, limit);
  }

  public List<ExternalProcessVariableDto> getVariableUpdatesIngestedAt(final Long ingestTimestamp) {
    log.debug(
        "Fetching variables that where ingested at {}", Instant.ofEpochMilli(ingestTimestamp));

    return variableRepository.getVariableUpdatesIngestedAt(ingestTimestamp);
  }
}
