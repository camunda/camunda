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
import io.camunda.optimize.service.db.writer.variable.ProcessVariableUpdateWriter;
import io.camunda.optimize.service.db.writer.variable.VariableUpdateInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class EngineDataProcessCleanupService extends CleanupService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(EngineDataProcessCleanupService.class);
  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessInstanceReader processInstanceReader;
  private final ProcessInstanceWriter processInstanceWriter;
  private final ProcessVariableUpdateWriter processVariableUpdateWriter;
  private final VariableUpdateInstanceWriter variableUpdateInstanceWriter;

  public EngineDataProcessCleanupService(
      final ConfigurationService configurationService,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessInstanceReader processInstanceReader,
      final ProcessInstanceWriter processInstanceWriter,
      final ProcessVariableUpdateWriter processVariableUpdateWriter,
      final VariableUpdateInstanceWriter variableUpdateInstanceWriter) {
    this.configurationService = configurationService;
    this.processDefinitionReader = processDefinitionReader;
    this.processInstanceReader = processInstanceReader;
    this.processInstanceWriter = processInstanceWriter;
    this.processVariableUpdateWriter = processVariableUpdateWriter;
    this.variableUpdateInstanceWriter = variableUpdateInstanceWriter;
  }

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
        "Performing cleanup on process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
        currentProcessDefinitionKey,
        cleanupConfigurationForKey.getTtl(),
        cleanupConfigurationForKey.getCleanupMode());

    final OffsetDateTime endDate = startTime.minus(cleanupConfigurationForKey.getTtl());
    switch (cleanupConfigurationForKey.getCleanupMode()) {
      case ALL:
        performInstanceDataCleanup(currentProcessDefinitionKey, endDate, getBatchSize());
        break;
      case VARIABLES:
        performVariableDataCleanup(currentProcessDefinitionKey, endDate, getBatchSize());
        break;
      default:
        throw new IllegalStateException(
            "Unsupported cleanup mode " + cleanupConfigurationForKey.getCleanupMode());
    }

    log.info(
        "Finished cleanup on process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
        currentProcessDefinitionKey,
        cleanupConfigurationForKey.getTtl(),
        cleanupConfigurationForKey.getCleanupMode());
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

  private void performVariableDataCleanup(
      final String definitionKey, final OffsetDateTime endDate, final int batchSize) {
    PageResultDto<String> currentPageOfProcessInstanceIds =
        processInstanceReader.getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
            definitionKey, endDate, batchSize);
    while (!currentPageOfProcessInstanceIds.isEmpty()) {
      final List<String> currentInstanceIds = currentPageOfProcessInstanceIds.getEntities();
      variableUpdateInstanceWriter.deleteByProcessInstanceIds(currentInstanceIds);
      processVariableUpdateWriter.deleteVariableDataByProcessInstanceIds(
          definitionKey, currentInstanceIds);

      currentPageOfProcessInstanceIds =
          processInstanceReader.getNextPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
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
