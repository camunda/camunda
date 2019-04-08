/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.variable.VariableWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.OptimizeCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.ProcessDefinitionCleanupConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.cleanup.OptimizeCleanupService.enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown;

@Component
public class OptimizeProcessCleanupService implements OptimizeCleanupService {
  private static final Logger logger = LoggerFactory.getLogger(OptimizeProcessCleanupService.class);

  private final ConfigurationService configurationService;

  private final ProcessDefinitionReader processDefinitionReader;
  private final CompletedProcessInstanceWriter processInstanceWriter;
  private final VariableWriter variableWriter;

  @Autowired
  public OptimizeProcessCleanupService(final ConfigurationService configurationService,
                                       final ProcessDefinitionReader processDefinitionReader,
                                       final CompletedProcessInstanceWriter processInstanceWriter,
                                       final VariableWriter variableWriter) {
    this.configurationService = configurationService;
    this.processDefinitionReader = processDefinitionReader;
    this.processInstanceWriter = processInstanceWriter;
    this.variableWriter = variableWriter;
  }

  @Override
  public void doCleanup(final OffsetDateTime startTime) {
    final Set<String> allOptimizeProcessDefinitionKeys = getAllOptimizeProcessDefinitionKeys();

    enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown(
      allOptimizeProcessDefinitionKeys,
      getCleanupConfiguration().getAllProcessSpecificConfigurationKeys()
    );
    int i = 1;
    for (String currentProcessDefinitionKey : allOptimizeProcessDefinitionKeys) {
      logger.info("Process History Cleanup step {}/{}", i, allOptimizeProcessDefinitionKeys.size());
      performCleanupForProcessKey(startTime, currentProcessDefinitionKey);
      i++;
    }
  }

  private void performCleanupForProcessKey(OffsetDateTime startTime, String currentProcessDefinitionKey) {
    final ProcessDefinitionCleanupConfiguration cleanupConfigurationForKey = getCleanupConfiguration()
      .getProcessDefinitionCleanupConfigurationForKey(currentProcessDefinitionKey);

    logger.info(
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
        variableWriter.deleteAllInstanceVariablesByProcessDefinitionKeyAndEndDateOlderThan(
          currentProcessDefinitionKey,
          endDateFilter
        );
        break;
      default:
        throw new IllegalStateException("Unsupported cleanup mode " + cleanupConfigurationForKey.getProcessDataCleanupMode());
    }

    logger.info(
      "Finished cleanup on process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
      currentProcessDefinitionKey,
      cleanupConfigurationForKey.getTtl(),
      cleanupConfigurationForKey.getProcessDataCleanupMode()
    );
  }

  private Set<String> getAllOptimizeProcessDefinitionKeys() {
    return processDefinitionReader.fetchFullyImportedProcessDefinitionsAsService()
      .stream()
      .map(ProcessDefinitionOptimizeDto::getKey)
      .collect(Collectors.toSet());
  }

  private OptimizeCleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

}
