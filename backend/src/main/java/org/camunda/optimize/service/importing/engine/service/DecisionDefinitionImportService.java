/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.DecisionDefinitionElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
public class DecisionDefinitionImportService implements ImportService<DecisionDefinitionEngineDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final DecisionDefinitionWriter decisionDefinitionWriter;

  @Override
  public void executeImport(final List<DecisionDefinitionEngineDto> pageOfEngineEntities,
                            final Runnable importCompleteCallback) {
    log.trace("Importing entities from engine...");
    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<DecisionDefinitionOptimizeDto> optimizeDtos = mapEngineEntitiesToOptimizeEntities(
        pageOfEngineEntities
      );
      final ElasticsearchImportJob<DecisionDefinitionOptimizeDto> elasticsearchImportJob = createElasticsearchImportJob(
        optimizeDtos, importCompleteCallback
      );
      elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private List<DecisionDefinitionOptimizeDto> mapEngineEntitiesToOptimizeEntities(
    final List<DecisionDefinitionEngineDto> engineDtos) {
    return engineDtos
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<DecisionDefinitionOptimizeDto> createElasticsearchImportJob(
    final List<DecisionDefinitionOptimizeDto> optimizeDtos,
    final Runnable importCompleteCallback) {
    final DecisionDefinitionElasticsearchImportJob importJob = new DecisionDefinitionElasticsearchImportJob(
      decisionDefinitionWriter, importCompleteCallback
    );
    importJob.setEntitiesToImport(optimizeDtos);
    return importJob;
  }

  private DecisionDefinitionOptimizeDto mapEngineEntityToOptimizeEntity(final DecisionDefinitionEngineDto engineDto) {
    return new DecisionDefinitionOptimizeDto(
      engineDto.getId(),
      engineDto.getKey(),
      String.valueOf(engineDto.getVersion()),
      engineDto.getVersionTag(),
      engineDto.getName(),
      engineContext.getEngineAlias(),
      engineDto.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null))
    );
  }

}
