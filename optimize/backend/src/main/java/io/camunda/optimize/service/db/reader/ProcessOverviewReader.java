/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.service.db.repository.ProcessRepository;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ProcessOverviewReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ProcessOverviewReader.class);
  private final ProcessRepository processRepository;

  public ProcessOverviewReader(final ProcessRepository processRepository) {
    this.processRepository = processRepository;
  }

  public Map<String, ProcessOverviewDto> getProcessOverviewsByKey(
      final Set<String> processDefinitionKeys) {
    return processRepository.getProcessOverviewsByKey(processDefinitionKeys);
  }

  public Optional<ProcessOverviewDto> getProcessOverviewByKey(final String processDefinitionKey) {
    final Map<String, ProcessOverviewDto> goalsForProcessesByKey =
        getProcessOverviewsByKey(Collections.singleton(processDefinitionKey));
    return Optional.ofNullable(goalsForProcessesByKey.get(processDefinitionKey));
  }

  public Map<String, ProcessDigestResponseDto> getAllActiveProcessDigestsByKey() {
    return processRepository.getAllActiveProcessDigestsByKey();
  }

  public Map<String, ProcessOverviewDto> getProcessOverviewsWithPendingOwnershipData() {
    return processRepository.getProcessOverviewsWithPendingOwnershipData();
  }
}
