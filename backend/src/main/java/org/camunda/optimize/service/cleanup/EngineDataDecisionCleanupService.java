/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.reader.DecisionInstanceReader;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.camunda.optimize.service.cleanup.CleanupService.enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown;

@AllArgsConstructor
@Component
@Slf4j
public class EngineDataDecisionCleanupService implements CleanupService {

  private final ConfigurationService configurationService;
  private final DecisionInstanceReader decisionInstanceReader;
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
    return decisionInstanceReader.getExistingDecisionDefinitionKeysFromInstances();
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

}
