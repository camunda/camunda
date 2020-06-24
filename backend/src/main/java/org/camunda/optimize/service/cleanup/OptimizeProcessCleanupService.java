/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
import org.camunda.optimize.service.util.configuration.cleanup.OptimizeCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.cleanup.OptimizeCleanupService.enforceAllSpecificDefinitionKeyConfigurationsHaveMatchInKnown;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;

@AllArgsConstructor
@Component
@Slf4j
public class OptimizeProcessCleanupService implements OptimizeCleanupService {

  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessInstanceReader processInstanceReader;
  private final CompletedProcessInstanceWriter processInstanceWriter;
  private final ProcessVariableUpdateWriter processVariableUpdateWriter;
  private final BusinessKeyWriter businessKeyWriter;
  private final CamundaActivityEventWriter camundaActivityEventWriter;
  private final VariableUpdateInstanceWriter variableUpdateInstanceWriter;

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

  private void performCleanupForProcessKey(final OffsetDateTime startTime, final String currentProcessDefinitionKey) {
    final ProcessDefinitionCleanupConfiguration cleanupConfigurationForKey = getCleanupConfiguration()
      .getProcessDefinitionCleanupConfigurationForKey(currentProcessDefinitionKey);

    log.info(
      "Performing cleanup on process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
      currentProcessDefinitionKey,
      cleanupConfigurationForKey.getTtl(),
      cleanupConfigurationForKey.getProcessDataCleanupMode()
    );

    final OffsetDateTime endDate = startTime.minus(cleanupConfigurationForKey.getTtl());
    switch (cleanupConfigurationForKey.getProcessDataCleanupMode()) {
      case ALL:
        performInstanceDataCleanup(currentProcessDefinitionKey, endDate);
        break;
      case VARIABLES:
        performVariableDataCleanup(currentProcessDefinitionKey, endDate);
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

  private void performInstanceDataCleanup(final String definitionKey, final OffsetDateTime endDate) {
    PageResultDto<String> currentPageOfProcessInstanceIds = processInstanceReader
      .getFirstPageOfProcessInstanceIdsThatEndedBefore(definitionKey, endDate, MAX_RESPONSE_SIZE_LIMIT);
    while (!currentPageOfProcessInstanceIds.isEmpty()) {
      final List<String> currentInstanceIds = currentPageOfProcessInstanceIds.getEntities();
      camundaActivityEventWriter.deleteByProcessInstanceIds(definitionKey, currentInstanceIds);
      businessKeyWriter.deleteByProcessInstanceIds(currentInstanceIds);
      variableUpdateInstanceWriter.deleteByProcessInstanceIds(currentInstanceIds);
      processInstanceWriter.deleteByIds(currentInstanceIds);
      currentPageOfProcessInstanceIds = processInstanceReader
        .getNextPageOfProcessInstanceIds(currentPageOfProcessInstanceIds);
    }
  }

  private void performVariableDataCleanup(final String definitionKey, final OffsetDateTime endDate) {
    PageResultDto<String> currentPageOfProcessInstanceIds = processInstanceReader
      .getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(definitionKey, endDate, MAX_RESPONSE_SIZE_LIMIT);
    while (!currentPageOfProcessInstanceIds.isEmpty()) {
      final List<String> currentInstanceIds = currentPageOfProcessInstanceIds.getEntities();
      variableUpdateInstanceWriter.deleteByProcessInstanceIds(currentInstanceIds);
      processVariableUpdateWriter.deleteVariableDataByProcessInstanceIds(currentInstanceIds);

      currentPageOfProcessInstanceIds = processInstanceReader
        .getNextPageOfProcessInstanceIds(currentPageOfProcessInstanceIds);
    }
  }

  private Set<String> getAllOptimizeProcessDefinitionKeys() {
    return processDefinitionReader.getProcessDefinitions(false, false)
      .stream()
      .map(ProcessDefinitionOptimizeDto::getKey)
      .collect(Collectors.toSet());
  }

  private OptimizeCleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

}
