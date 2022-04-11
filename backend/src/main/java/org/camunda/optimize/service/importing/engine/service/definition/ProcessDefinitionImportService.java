/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.definition;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ProcessDefinitionElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Slf4j
public class ProcessDefinitionImportService implements ImportService<ProcessDefinitionEngineDto> {

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public ProcessDefinitionImportService(final ConfigurationService configurationService,
                                        final EngineContext engineContext,
                                        final ProcessDefinitionWriter processDefinitionWriter,
                                        final ProcessDefinitionResolverService processDefinitionResolverService) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.engineContext = engineContext;
    this.processDefinitionWriter = processDefinitionWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
  }

  @Override
  public void executeImport(final List<ProcessDefinitionEngineDto> pageOfEngineEntities,
                            final Runnable importCompleteCallback) {
    log.trace("Importing process definitions from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessDefinitionOptimizeDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities
        (pageOfEngineEntities);
      markSavedDefinitionsAsDeleted(newOptimizeEntities);
      final ElasticsearchImportJob<ProcessDefinitionOptimizeDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void markSavedDefinitionsAsDeleted(final List<ProcessDefinitionOptimizeDto> definitionsToImport) {
    final boolean definitionsDeleted = processDefinitionWriter.markRedeployedDefinitionsAsDeleted(definitionsToImport);
    // We only resync the cache if at least one existing definition has been marked as deleted
    if (definitionsDeleted) {
      processDefinitionResolverService.syncCache();
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessDefinitionOptimizeDto> mapEngineEntitiesToOptimizeEntities(
    List<ProcessDefinitionEngineDto> engineEntities) {
    // we mark new definitions as deleted if they are imported in the same batch as a newer deployment
    final Map<String, List<ProcessDefinitionEngineDto>> groupedDefinitions = engineEntities.stream()
      .collect(groupingBy(definition -> definition.getKey() + definition.getTenantId() + definition.getVersion()));
    groupedDefinitions.entrySet()
      .stream()
      .filter(entry -> entry.getValue().size() > 1)
      .forEach(entry -> {
        final ProcessDefinitionEngineDto newestDefinition = entry.getValue()
          .stream()
          .max(Comparator.comparing(ProcessDefinitionEngineDto::getDeploymentTime))
          .get();
        entry.getValue()
          .stream()
          .filter(definition -> !definition.equals(newestDefinition))
          .forEach(deletedDef -> deletedDef.setDeleted(true));
      });
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<ProcessDefinitionOptimizeDto> createElasticsearchImportJob(
    final List<ProcessDefinitionOptimizeDto> processDefinitions,
    final Runnable importCompleteCallback) {
    ProcessDefinitionElasticsearchImportJob procDefImportJob = new ProcessDefinitionElasticsearchImportJob(
      processDefinitionWriter, importCompleteCallback
    );
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  private ProcessDefinitionOptimizeDto mapEngineEntityToOptimizeEntity(ProcessDefinitionEngineDto engineEntity) {
    return ProcessDefinitionOptimizeDto.builder()
      .id(engineEntity.getId())
      .key(engineEntity.getKey())
      .version(String.valueOf(engineEntity.getVersion()))
      .versionTag(engineEntity.getVersionTag())
      .name(engineEntity.getName())
      .dataSource(new EngineDataSourceDto(engineContext.getEngineAlias()))
      .tenantId(engineEntity.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null)))
      .deleted(engineEntity.isDeleted())
      .build();
  }

}
