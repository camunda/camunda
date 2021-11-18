/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.ingested.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ExternalVariableUpdateElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto.toPluginVariableDtos;

@Slf4j
public class ExternalVariableUpdateImportService implements ImportService<ExternalProcessVariableDto> {

  public static final long DEFAULT_VERSION = 1000L;

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final ProcessVariableUpdateWriter variableWriter;
  private final ConfigurationService configurationService;
  private final ObjectVariableService objectVariableService;

  public ExternalVariableUpdateImportService(final ConfigurationService configurationService,
                                             final ProcessVariableUpdateWriter variableWriter,
                                             final ObjectVariableService objectVariableService) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.variableWriter = variableWriter;
    this.configurationService = configurationService;
    this.objectVariableService = objectVariableService;
  }

  @Override
  public void executeImport(final List<ExternalProcessVariableDto> pageOfExternalEntities,
                            final Runnable importCompleteCallback) {
    log.trace("Importing external variable entities...");

    boolean newDataIsAvailable = !pageOfExternalEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessVariableDto> newOptimizeEntities = mapExternalEntitiesToOptimizeEntities(pageOfExternalEntities);
      ElasticsearchImportJob<ProcessVariableDto> elasticsearchImportJob = createElasticsearchImportJob(
        newOptimizeEntities,
        importCompleteCallback
      );
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob<?> elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessVariableDto> mapExternalEntitiesToOptimizeEntities(final List<ExternalProcessVariableDto> externalEntities) {
    final List<ExternalProcessVariableDto> deduplicatedVariables =
      resolveDuplicateVariableUpdatesPerProcessInstance(externalEntities);
    List<PluginVariableDto> pluginVariableList = toPluginVariableDtos(deduplicatedVariables);
    pluginVariableList = objectVariableService.convertObjectVariablesForImport(pluginVariableList);
    return pluginVariableList.stream()
      .map(this::convertPluginVariableToImportVariable)
      .collect(toList());
  }

  private List<ExternalProcessVariableDto> resolveDuplicateVariableUpdatesPerProcessInstance(
    final List<ExternalProcessVariableDto> externalEntities) {
    // if we have more than one variable update for the same variable within one process instance, we only import the
    // variable with the latest ingestion timestamp
    List<ExternalProcessVariableDto> deduplicatedVariables = new ArrayList<>();
    Map<String, List<ExternalProcessVariableDto>> variablesByProcessInstanceId = new HashMap<>();
    for (ExternalProcessVariableDto variable : externalEntities) {
      variablesByProcessInstanceId.putIfAbsent(variable.getProcessInstanceId(), new ArrayList<>());
      variablesByProcessInstanceId.get(variable.getProcessInstanceId()).add(variable);
    }
    variablesByProcessInstanceId
      .forEach((id, vars) -> deduplicatedVariables.addAll(resolveDuplicateVariableUpdates(vars)));
    return deduplicatedVariables;
  }

  private Set<ExternalProcessVariableDto> resolveDuplicateVariableUpdates(final List<ExternalProcessVariableDto> externalEntities) {
    return new HashSet<>(
      externalEntities.stream().collect(toMap(
        ExternalProcessVariableDto::getVariableId,
        Function.identity(),
        (var1, var2) ->
          // if there is more than one update for the same variable, the update with the latest
          // ingestion timestamp wins
          var1.getIngestionTimestamp().compareTo(var2.getIngestionTimestamp()) > 0 ? var1 : var2
      )).values());
  }

  private ProcessVariableDto convertPluginVariableToImportVariable(final PluginVariableDto pluginVariableDto) {
    return new ProcessVariableDto(
      pluginVariableDto.getId(),
      pluginVariableDto.getName(),
      pluginVariableDto.getType(),
      pluginVariableDto.getValue(),
      pluginVariableDto.getTimestamp(),
      null,
      pluginVariableDto.getProcessDefinitionKey(),
      null,
      pluginVariableDto.getProcessInstanceId(),
      // defaulting to the same version as there is no versioning for external variables
      DEFAULT_VERSION,
      null,
      null
    );
  }

  private ElasticsearchImportJob<ProcessVariableDto> createElasticsearchImportJob(final List<ProcessVariableDto> processVariables,
                                                                                  final Runnable callback) {
    final ExternalVariableUpdateElasticsearchImportJob importJob = new ExternalVariableUpdateElasticsearchImportJob(
      variableWriter, configurationService, callback
    );
    importJob.setEntitiesToImport(processVariables);
    return importJob;
  }

}
