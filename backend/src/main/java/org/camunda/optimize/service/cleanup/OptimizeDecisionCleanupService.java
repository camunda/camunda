/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.DecisionDefinitionCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.OptimizeCleanupConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.cleanup.OptimizeCleanupService.enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown;

@Component
public class OptimizeDecisionCleanupService implements OptimizeCleanupService {
  private static final Logger logger = LoggerFactory.getLogger(OptimizeDecisionCleanupService.class);

  private final ConfigurationService configurationService;

  private final DecisionDefinitionReader decisionDefinitionReader;
  private final DecisionInstanceWriter decisionInstanceWriter;

  @Autowired
  public OptimizeDecisionCleanupService(final ConfigurationService configurationService,
                                        final DecisionDefinitionReader decisionDefinitionReader,
                                        final DecisionInstanceWriter decisionInstanceWriter) {
    this.configurationService = configurationService;
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.decisionInstanceWriter = decisionInstanceWriter;
  }

  @Override
  public void doCleanup(final OffsetDateTime startTime) {
    final Set<String> allOptimizeProcessDefinitionKeys = getAllOptimizeDecisionDefinitionKeys();

    enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown(
      allOptimizeProcessDefinitionKeys,
      getCleanupConfiguration().getAllDecisionSpecificConfigurationKeys()
    );
    int i = 1;
    for (String currentProcessDefinitionKey : allOptimizeProcessDefinitionKeys) {
      logger.info("Decision History Cleanup step {}/{}", i, allOptimizeProcessDefinitionKeys.size());
      performCleanupForDecisionKey(startTime, currentProcessDefinitionKey);
      i++;
    }
  }

  private void performCleanupForDecisionKey(OffsetDateTime startTime, String decisionDefinitionKey) {
    final DecisionDefinitionCleanupConfiguration cleanupConfigurationForKey = getCleanupConfiguration()
      .getDecisionDefinitionCleanupConfigurationForKey(decisionDefinitionKey);

    logger.info(
      "Performing cleanup on decision instances for decisionDefinitionKey: {}, with ttl: {}",
      decisionDefinitionKey, cleanupConfigurationForKey.getTtl()
    );

    final OffsetDateTime endDateFilter = startTime.minus(cleanupConfigurationForKey.getTtl());

    decisionInstanceWriter.deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(
      decisionDefinitionKey, endDateFilter
    );


    logger.info(
      "Finished cleanup on decision instances for decisionDefinitionKey: {}, with ttl: {}",
      decisionDefinitionKey, cleanupConfigurationForKey.getTtl()
    );
  }

  private Set<String> getAllOptimizeDecisionDefinitionKeys() {
    return decisionDefinitionReader.fetchFullyImportedDecisionDefinitionsAsService()
      .stream()
      .map(DecisionDefinitionOptimizeDto::getKey)
      .collect(Collectors.toSet());
  }

  private OptimizeCleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

}
