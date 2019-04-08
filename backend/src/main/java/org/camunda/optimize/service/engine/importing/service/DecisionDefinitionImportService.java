/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.DecisionDefinitionElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class DecisionDefinitionImportService implements ImportService<DecisionDefinitionEngineDto> {
  private static final Logger logger = LoggerFactory.getLogger(DecisionDefinitionImportService.class);

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final String engineAlias;
  private final DecisionDefinitionWriter decisionDefinitionWriter;

  public DecisionDefinitionImportService(final DecisionDefinitionWriter decisionDefinitionWriter,
                                         final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                         final EngineContext engineContext) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineAlias = engineContext.getEngineAlias();
    this.decisionDefinitionWriter = decisionDefinitionWriter;
  }

  @Override
  public void executeImport(final List<DecisionDefinitionEngineDto> engineDtoList) {
    logger.trace("Importing entities from engine...");
    final boolean newDataIsAvailable = !engineDtoList.isEmpty();
    if (newDataIsAvailable) {
      final List<DecisionDefinitionOptimizeDto> optimizeDtos = mapEngineEntitiesToOptimizeEntities(
        engineDtoList
      );
      final ElasticsearchImportJob<DecisionDefinitionOptimizeDto> elasticsearchImportJob = createElasticsearchImportJob(
        optimizeDtos
      );
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public void executeImport(final List<DecisionDefinitionEngineDto> pageOfEngineEntities, final Runnable callback) {
    executeImport(pageOfEngineEntities);
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<DecisionDefinitionOptimizeDto> mapEngineEntitiesToOptimizeEntities(
    final List<DecisionDefinitionEngineDto> engineDtos) {
    return engineDtos
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<DecisionDefinitionOptimizeDto> createElasticsearchImportJob(
    final List<DecisionDefinitionOptimizeDto> optimizeDtos) {
    final DecisionDefinitionElasticsearchImportJob importJob = new DecisionDefinitionElasticsearchImportJob(
      decisionDefinitionWriter
    );
    importJob.setEntitiesToImport(optimizeDtos);
    return importJob;
  }

  private DecisionDefinitionOptimizeDto mapEngineEntityToOptimizeEntity(final DecisionDefinitionEngineDto engineDto) {
    final DecisionDefinitionOptimizeDto optimizeDto = new DecisionDefinitionOptimizeDto();
    optimizeDto.setName(engineDto.getName());
    optimizeDto.setKey(engineDto.getKey());
    optimizeDto.setId(engineDto.getId());
    optimizeDto.setVersion(String.valueOf(engineDto.getVersion()));
    optimizeDto.setEngine(engineAlias);
    return optimizeDto;
  }

}
