/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import io.camunda.optimize.service.db.writer.DecisionInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import java.time.OffsetDateTime;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class EngineDataDecisionCleanupService extends CleanupService {

  private final ConfigurationService configurationService;
  private final DecisionDefinitionReader decisionDefinitionReader;
  private final DecisionInstanceWriter decisionInstanceWriter;

  @Override
  public boolean isEnabled() {
    return getCleanupConfiguration().getDecisionCleanupConfiguration().isEnabled();
  }

  @Override
  public void doCleanup(final OffsetDateTime startTime) {
    final Set<String> allOptimizeProcessDefinitionKeys = getAllOptimizeDecisionDefinitionKeys();

    verifyConfiguredKeysAreKnownDefinitionKeys(
        allOptimizeProcessDefinitionKeys,
        getCleanupConfiguration()
            .getDecisionCleanupConfiguration()
            .getAllDecisionSpecificConfigurationKeys());
    int i = 1;
    for (String currentProcessDefinitionKey : allOptimizeProcessDefinitionKeys) {
      log.info("Decision History Cleanup step {}/{}", i, allOptimizeProcessDefinitionKeys.size());
      performCleanupForDecisionKey(startTime, currentProcessDefinitionKey);
      i++;
    }
  }

  private void performCleanupForDecisionKey(
      OffsetDateTime startTime, String decisionDefinitionKey) {
    final DecisionDefinitionCleanupConfiguration cleanupConfigurationForKey =
        getCleanupConfiguration()
            .getDecisionDefinitionCleanupConfigurationForKey(decisionDefinitionKey);

    log.info(
        "Performing cleanup on decision instances for decisionDefinitionKey: {}, with ttl: {}",
        decisionDefinitionKey,
        cleanupConfigurationForKey.getTtl());

    final OffsetDateTime endDateFilter = startTime.minus(cleanupConfigurationForKey.getTtl());

    decisionInstanceWriter.deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(
        decisionDefinitionKey, endDateFilter);

    log.info(
        "Finished cleanup on decision instances for decisionDefinitionKey: {}, with ttl: {}",
        decisionDefinitionKey,
        cleanupConfigurationForKey.getTtl());
  }

  private Set<String> getAllOptimizeDecisionDefinitionKeys() {
    return decisionDefinitionReader.getAllDecisionDefinitions().stream()
        .map(DecisionDefinitionOptimizeDto::getKey)
        .collect(toSet());
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }
}
