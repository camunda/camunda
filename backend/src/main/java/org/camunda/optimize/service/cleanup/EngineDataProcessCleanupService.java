/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.PageResultDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.reader.ProcessInstanceReader;
import org.camunda.optimize.service.es.writer.BusinessKeyWriter;
import org.camunda.optimize.service.es.writer.CamundaActivityEventWriter;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.es.writer.variable.VariableUpdateInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.cleanup.CleanupService.enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown;

@AllArgsConstructor
@Component
@Slf4j
public class EngineDataProcessCleanupService implements CleanupService {

  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessInstanceReader processInstanceReader;
  private final CompletedProcessInstanceWriter processInstanceWriter;
  private final ProcessVariableUpdateWriter processVariableUpdateWriter;
  private final BusinessKeyWriter businessKeyWriter;
  private final CamundaActivityEventWriter camundaActivityEventWriter;
  private final VariableUpdateInstanceWriter variableUpdateInstanceWriter;

  @Override
  public boolean isEnabled() {
    return getCleanupConfiguration().getProcessDataCleanupConfiguration().isEnabled();
  }

  @Override
  public void doCleanup(final OffsetDateTime startTime) {
    final Set<String> allOptimizeProcessDefinitionKeys = getAllCamundaEngineProcessDefinitionKeys();

    enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown(
      allOptimizeProcessDefinitionKeys,
      getCleanupConfiguration().getProcessDataCleanupConfiguration().getAllProcessSpecificConfigurationKeys()
    );
    int i = 1;
    for (String currentProcessDefinitionKey : allOptimizeProcessDefinitionKeys) {
      log.info("Process History Cleanup step {}/{}", i, allOptimizeProcessDefinitionKeys.size());
      performCleanupForProcessKey(startTime, currentProcessDefinitionKey);
      i++;
    }
  }

  private void performCleanupForProcessKey(final OffsetDateTime startTime, final String currentProcessDefinitionKey) {
    final ProcessDefinitionCleanupConfiguration cleanupConfigurationForKey = getCleanupConfiguration()
      .getProcessDefinitionCleanupConfigurationForKey(currentProcessDefinitionKey);

    log.info(
      "Performing cleanup on process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
      currentProcessDefinitionKey,
      cleanupConfigurationForKey.getTtl(),
      cleanupConfigurationForKey.getCleanupMode()
    );

    final OffsetDateTime endDate = startTime.minus(cleanupConfigurationForKey.getTtl());
    switch (cleanupConfigurationForKey.getCleanupMode()) {
      case ALL:
        performInstanceDataCleanup(currentProcessDefinitionKey, endDate, getBatchSize());
        break;
      case VARIABLES:
        performVariableDataCleanup(currentProcessDefinitionKey, endDate, getBatchSize());
        break;
      default:
        throw new IllegalStateException("Unsupported cleanup mode " + cleanupConfigurationForKey.getCleanupMode());
    }

    log.info(
      "Finished cleanup on process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
      currentProcessDefinitionKey,
      cleanupConfigurationForKey.getTtl(),
      cleanupConfigurationForKey.getCleanupMode()
    );
  }

  private void performInstanceDataCleanup(final String definitionKey,
                                          final OffsetDateTime endDate,
                                          final int batchSize) {
    PageResultDto<String> currentPageOfProcessInstanceIds = processInstanceReader
      .getFirstPageOfProcessInstanceIdsThatEndedBefore(definitionKey, endDate, batchSize);
    while (!currentPageOfProcessInstanceIds.isEmpty()) {
      final List<String> currentInstanceIds = currentPageOfProcessInstanceIds.getEntities();
      camundaActivityEventWriter.deleteByProcessInstanceIds(definitionKey, currentInstanceIds);
      businessKeyWriter.deleteByProcessInstanceIds(currentInstanceIds);
      variableUpdateInstanceWriter.deleteByProcessInstanceIds(currentInstanceIds);
      processInstanceWriter.deleteByIds(definitionKey, currentInstanceIds);
      currentPageOfProcessInstanceIds = processInstanceReader
        .getNextPageOfProcessInstanceIdsThatEndedBefore(
          definitionKey, endDate, batchSize, currentPageOfProcessInstanceIds
        );
    }
  }

  private void performVariableDataCleanup(final String definitionKey,
                                          final OffsetDateTime endDate,
                                          final int batchSize) {
    PageResultDto<String> currentPageOfProcessInstanceIds = processInstanceReader
      .getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(definitionKey, endDate, batchSize);
    while (!currentPageOfProcessInstanceIds.isEmpty()) {
      final List<String> currentInstanceIds = currentPageOfProcessInstanceIds.getEntities();
      variableUpdateInstanceWriter.deleteByProcessInstanceIds(currentInstanceIds);
      processVariableUpdateWriter.deleteVariableDataByProcessInstanceIds(definitionKey, currentInstanceIds);

      currentPageOfProcessInstanceIds = processInstanceReader
        .getNextPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
          definitionKey, endDate, batchSize, currentPageOfProcessInstanceIds
        );
    }
  }

  private Set<String> getAllCamundaEngineProcessDefinitionKeys() {
    final Set<String> existingDefinitionKeys = processInstanceReader.getExistingProcessDefinitionKeysFromInstances();
    return processDefinitionReader.getProcessDefinitions(existingDefinitionKeys)
      .stream()
      .filter(definition -> !definition.isEventBased())
      .map(ProcessDefinitionOptimizeDto::getKey)
      .collect(toSet());
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

  private int getBatchSize() {
    return getCleanupConfiguration().getProcessDataCleanupConfiguration().getBatchSize();
  }

}
