/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.definition;

import static java.util.stream.Collectors.groupingBy;

import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.ProcessDefinitionDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProcessDefinitionImportService implements ImportService<ProcessDefinitionEngineDto> {

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final EngineContext engineContext;
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final DatabaseClient databaseClient;

  public ProcessDefinitionImportService(
      final ConfigurationService configurationService,
      final EngineContext engineContext,
      final ProcessDefinitionWriter processDefinitionWriter,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.engineContext = engineContext;
    this.processDefinitionWriter = processDefinitionWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ProcessDefinitionEngineDto> pageOfEngineEntities,
      final Runnable importCompleteCallback) {
    log.trace("Importing process definitions from engine...");

    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessDefinitionOptimizeDto> newOptimizeEntities =
          mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      markSavedDefinitionsAsDeleted(newOptimizeEntities);
      final DatabaseImportJob<ProcessDefinitionOptimizeDto> databaseImportJob =
          createDatabaseImportJob(newOptimizeEntities, importCompleteCallback);
      addDatabaseImportJobToQueue(databaseImportJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private void markSavedDefinitionsAsDeleted(
      final List<ProcessDefinitionOptimizeDto> definitionsToImport) {
    final boolean definitionsDeleted =
        processDefinitionWriter.markRedeployedDefinitionsAsDeleted(definitionsToImport);
    // We only resync the cache if at least one existing definition has been marked as deleted
    if (definitionsDeleted) {
      processDefinitionResolverService.syncCache();
    }
  }

  private void addDatabaseImportJobToQueue(final DatabaseImportJob databaseImportJob) {
    databaseImportJobExecutor.executeImportJob(databaseImportJob);
  }

  private List<ProcessDefinitionOptimizeDto> mapEngineEntitiesToOptimizeEntities(
      final List<ProcessDefinitionEngineDto> engineEntities) {
    // we mark new definitions as deleted if they are imported in the same batch as a newer
    // deployment
    final Map<String, List<ProcessDefinitionEngineDto>> groupedDefinitions =
        engineEntities.stream()
            .collect(
                groupingBy(
                    definition ->
                        definition.getKey() + definition.getTenantId() + definition.getVersion()));
    groupedDefinitions.entrySet().stream()
        .filter(entry -> entry.getValue().size() > 1)
        .forEach(
            entry -> {
              final ProcessDefinitionEngineDto newestDefinition =
                  entry.getValue().stream()
                      .max(Comparator.comparing(ProcessDefinitionEngineDto::getDeploymentTime))
                      .get();
              entry.getValue().stream()
                  .filter(definition -> !definition.equals(newestDefinition))
                  .forEach(deletedDef -> deletedDef.setDeleted(true));
            });
    return engineEntities.stream()
        .map(this::mapEngineEntityToOptimizeEntity)
        .collect(Collectors.toList());
  }

  private DatabaseImportJob<ProcessDefinitionOptimizeDto> createDatabaseImportJob(
      final List<ProcessDefinitionOptimizeDto> processDefinitions,
      final Runnable importCompleteCallback) {
    final ProcessDefinitionDatabaseImportJob procDefImportJob =
        new ProcessDefinitionDatabaseImportJob(
            processDefinitionWriter, importCompleteCallback, databaseClient);
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  private ProcessDefinitionOptimizeDto mapEngineEntityToOptimizeEntity(
      final ProcessDefinitionEngineDto engineEntity) {
    return ProcessDefinitionOptimizeDto.builder()
        .id(engineEntity.getId())
        .key(engineEntity.getKey())
        .version(String.valueOf(engineEntity.getVersion()))
        .versionTag(engineEntity.getVersionTag())
        .name(engineEntity.getName())
        .dataSource(new EngineDataSourceDto(engineContext.getEngineAlias()))
        .tenantId(
            engineEntity
                .getTenantId()
                .orElseGet(() -> engineContext.getDefaultTenantId().orElse(null)))
        .deleted(engineEntity.isDeleted())
        // if it is v1, then we assume that it is not yet onboarded. Otherwise, we assume it already
        // is onboarded
        .onboarded(engineEntity.getVersion() != 1)
        .build();
  }
}
