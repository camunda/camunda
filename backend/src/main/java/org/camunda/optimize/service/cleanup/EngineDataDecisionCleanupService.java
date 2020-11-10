/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.cleanup.CleanupService.enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown;

@AllArgsConstructor
@Component
@Slf4j
public class EngineDataDecisionCleanupService implements CleanupService {

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

    enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown(
      allOptimizeProcessDefinitionKeys,
      getCleanupConfiguration().getDecisionCleanupConfiguration().getAllDecisionSpecificConfigurationKeys()
    );
    int i = 1;
    for (String currentProcessDefinitionKey : allOptimizeProcessDefinitionKeys) {
      log.info("Decision History Cleanup step {}/{}", i, allOptimizeProcessDefinitionKeys.size());
      performCleanupForDecisionKey(startTime, currentProcessDefinitionKey);
      i++;
    }
  }

  private void performCleanupForDecisionKey(OffsetDateTime startTime, String decisionDefinitionKey) {
    final DecisionDefinitionCleanupConfiguration cleanupConfigurationForKey = getCleanupConfiguration()
      .getDecisionDefinitionCleanupConfigurationForKey(decisionDefinitionKey);

    log.info(
      "Performing cleanup on decision instances for decisionDefinitionKey: {}, with ttl: {}",
      decisionDefinitionKey, cleanupConfigurationForKey.getTtl()
    );

    final OffsetDateTime endDateFilter = startTime.minus(cleanupConfigurationForKey.getTtl());

    decisionInstanceWriter.deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(
      decisionDefinitionKey, endDateFilter
    );


    log.info(
      "Finished cleanup on decision instances for decisionDefinitionKey: {}, with ttl: {}",
      decisionDefinitionKey, cleanupConfigurationForKey.getTtl()
    );
  }

  private Set<String> getAllOptimizeDecisionDefinitionKeys() {
    return decisionDefinitionReader.getDecisionDefinitions(false, false, true)
      .stream()
      .map(DecisionDefinitionOptimizeDto::getKey)
      .collect(Collectors.toSet());
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

}
