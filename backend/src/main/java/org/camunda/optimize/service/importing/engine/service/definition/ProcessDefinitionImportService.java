/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service.definition;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ProcessDefinitionElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.engine.service.ImportService;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
public class ProcessDefinitionImportService implements ImportService<ProcessDefinitionEngineDto> {

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  @Override
  public void executeImport(final List<ProcessDefinitionEngineDto> pageOfEngineEntities,
                            final Runnable importCompleteCallback) {
    log.trace("Importing process definitions from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessDefinitionOptimizeDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities
        (pageOfEngineEntities);
      markImportedDefinitionsAsDeleted(newOptimizeEntities);
      final ElasticsearchImportJob<ProcessDefinitionOptimizeDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void markImportedDefinitionsAsDeleted(final List<ProcessDefinitionOptimizeDto> definitionsToImport) {
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

  private List<ProcessDefinitionOptimizeDto> mapEngineEntitiesToOptimizeEntities(List<ProcessDefinitionEngineDto>
                                                                                   engineEntities) {
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
    return new ProcessDefinitionOptimizeDto(
      engineEntity.getId(),
      engineEntity.getKey(),
      String.valueOf(engineEntity.getVersion()),
      engineEntity.getVersionTag(),
      engineEntity.getName(),
      engineContext.getEngineAlias(),
      engineEntity.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null))
    );
  }

}
