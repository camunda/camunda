/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.service.db.repository.VariableRepository;
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
