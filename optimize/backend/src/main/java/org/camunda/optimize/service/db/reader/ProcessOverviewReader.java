/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.service.db.repository.ProcessRepository;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessOverviewReader {
  private final ProcessRepository processRepository;

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
