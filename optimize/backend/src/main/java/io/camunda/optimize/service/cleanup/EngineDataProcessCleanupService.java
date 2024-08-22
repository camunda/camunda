/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.PageResultDto;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessInstanceReader;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.db.writer.variable.VariableUpdateInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class EngineDataProcessCleanupService extends CleanupService {

  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessInstanceReader processInstanceReader;
  private final ProcessInstanceWriter processInstanceWriter;
  private final VariableUpdateInstanceWriter variableUpdateInstanceWriter;

  @Override
  public boolean isEnabled() {
    return getCleanupConfiguration().getProcessDataCleanupConfiguration().isEnabled();
  }

  @Override
  public void doCleanup(final OffsetDateTime startTime) {
    final Set<String> allOptimizeProcessDefinitionKeys = getAllCamundaEngineProcessDefinitionKeys();

    verifyConfiguredKeysAreKnownDefinitionKeys(
        allOptimizeProcessDefinitionKeys,
        getCleanupConfiguration()
            .getProcessDataCleanupConfiguration()
            .getAllProcessSpecificConfigurationKeys());
    int i = 1;
    for (final String currentProcessDefinitionKey : allOptimizeProcessDefinitionKeys) {
      log.info("Process History Cleanup step {}/{}", i, allOptimizeProcessDefinitionKeys.size());
      performCleanupForProcessKey(startTime, currentProcessDefinitionKey);
      i++;
    }
  }

  private void performCleanupForProcessKey(
      final OffsetDateTime startTime, final String currentProcessDefinitionKey) {
    final ProcessDefinitionCleanupConfiguration cleanupConfigurationForKey =
        getCleanupConfiguration()
            .getProcessDefinitionCleanupConfigurationForKey(currentProcessDefinitionKey);

    log.info(
        "Performing cleanup on process instances for processDefinitionKey: {}, with ttl: {}.",
        currentProcessDefinitionKey,
        cleanupConfigurationForKey.getTtl());

    final OffsetDateTime endDate = startTime.minus(cleanupConfigurationForKey.getTtl());
    performInstanceDataCleanup(currentProcessDefinitionKey, endDate, getBatchSize());

    log.info(
        "Finished cleanup on process instances for processDefinitionKey: {}, with ttl: {}.",
        currentProcessDefinitionKey,
        cleanupConfigurationForKey.getTtl());
  }

  private void performInstanceDataCleanup(
      final String definitionKey, final OffsetDateTime endDate, final int batchSize) {
    PageResultDto<String> currentPageOfProcessInstanceIds =
        processInstanceReader.getFirstPageOfProcessInstanceIdsThatEndedBefore(
            definitionKey, endDate, batchSize);
    while (!currentPageOfProcessInstanceIds.isEmpty()) {
      final List<String> currentInstanceIds = currentPageOfProcessInstanceIds.getEntities();
      variableUpdateInstanceWriter.deleteByProcessInstanceIds(currentInstanceIds);
      processInstanceWriter.deleteByIds(definitionKey, currentInstanceIds);
      currentPageOfProcessInstanceIds =
          processInstanceReader.getNextPageOfProcessInstanceIdsThatEndedBefore(
              definitionKey, endDate, batchSize, currentPageOfProcessInstanceIds);
    }
  }

  private Set<String> getAllCamundaEngineProcessDefinitionKeys() {
    return processDefinitionReader.getAllProcessDefinitions().stream()
        .map(ProcessDefinitionOptimizeDto::getKey)
        .collect(toSet());
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return configurationService.getCleanupServiceConfiguration();
  }

  private int getBatchSize() {
    return getCleanupConfiguration().getProcessDataCleanupConfiguration().getBatchSize();
  }
}
