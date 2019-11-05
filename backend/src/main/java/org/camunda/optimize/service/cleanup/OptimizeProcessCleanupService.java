/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.cleanup.OptimizeCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.cleanup.OptimizeCleanupService.enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown;

@AllArgsConstructor
@Component
@Slf4j
public class OptimizeProcessCleanupService implements OptimizeCleanupService {

  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final CompletedProcessInstanceWriter processInstanceWriter;
  private final ProcessVariableUpdateWriter processVariableUpdateWriter;


  @Override
  public void doCleanup(final OffsetDateTime startTime) {
    final Set<String> allOptimizeProcessDefinitionKeys = getAllOptimizeProcessDefinitionKeys();

    enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown(
      allOptimizeProcessDefinitionKeys,
      getCleanupConfiguration().getAllProcessSpecificConfigurationKeys()
    );
    int i = 1;
    for (String currentProcessDefinitionKey : allOptimizeProcessDefinitionKeys) {
      log.info("Process History Cleanup step {}/{}", i, allOptimizeProcessDefinitionKeys.size());
      performCleanupForProcessKey(startTime, currentProcessDefinitionKey);
      i++;
    }
  }

  private void performCleanupForProcessKey(OffsetDateTime startTime, String currentProcessDefinitionKey) {
    final ProcessDefinitionCleanupConfiguration cleanupConfigurationForKey = getCleanupConfiguration()
      .getProcessDefinitionCleanupConfigurationForKey(currentProcessDefinitionKey);

    log.info(
      "Performing cleanup on process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
      currentProcessDefinitionKey,
      cleanupConfigurationForKey.getTtl(),
      cleanupConfigurationForKey.getProcessDataCleanupMode()
    );

    final OffsetDateTime endDateFilter = startTime.minus(cleanupConfigurationForKey.getTtl());
    switch (cleanupConfigurationForKey.getProcessDataCleanupMode()) {
      case ALL:
        processInstanceWriter.deleteProcessInstancesByProcessDefinitionKeyAndEndDateOlderThan(
          currentProcessDefinitionKey,
          endDateFilter
        );
        break;
      case VARIABLES:
        processVariableUpdateWriter.deleteAllInstanceVariablesByProcessDefinitionKeyAndEndDateOlderThan(
          currentProcessDefinitionKey,
          endDateFilter
        );
        break;
      default:
        throw new IllegalStateException("Unsupported cleanup mode " + cleanupConfigurationForKey.getProcessDataCleanupMode());
    }

    log.info(
      "Finished cleanup on process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
      currentProcessDefinitionKey,
      cleanupConfigurationForKey.getTtl(),
      cleanupConfigurationForKey.getProcessDataCleanupMode()
    );
  }

  private Set<String> getAllOptimizeProcessDefinitionKeys() {
    return processDefinitionReader.getFullyImportedProcessDefinitions(false)
      .stream()
      .map(ProcessDefinitionOptimizeDto::getKey)
      .collect(Collectors.toSet());
  }

  private OptimizeCleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

}
