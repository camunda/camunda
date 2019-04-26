/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.DecisionDefinitionXmlElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
public class DecisionDefinitionXmlImportService implements ImportService<DecisionDefinitionXmlEngineDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;

  @Override
  public void executeImport(final List<DecisionDefinitionXmlEngineDto> engineDtoList) {
    log.trace("Importing entities from engine...");
    final boolean newDataIsAvailable = !engineDtoList.isEmpty();
    if (newDataIsAvailable) {
      final List<DecisionDefinitionOptimizeDto> optimizeDtos = mapEngineEntitiesToOptimizeEntities(engineDtoList);
      final ElasticsearchImportJob<DecisionDefinitionOptimizeDto> elasticsearchImportJob = createElasticsearchImportJob(
        optimizeDtos
      );
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public void executeImport(final List<DecisionDefinitionXmlEngineDto> pageOfEngineEntities, final Runnable callback) {
    executeImport(pageOfEngineEntities);
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<DecisionDefinitionOptimizeDto> mapEngineEntitiesToOptimizeEntities(
    final List<DecisionDefinitionXmlEngineDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<DecisionDefinitionOptimizeDto> createElasticsearchImportJob(
    final List<DecisionDefinitionOptimizeDto> optimizeDtos) {
    DecisionDefinitionXmlElasticsearchImportJob importJob = new DecisionDefinitionXmlElasticsearchImportJob(
      decisionDefinitionXmlWriter
    );
    importJob.setEntitiesToImport(optimizeDtos);
    return importJob;
  }

  private DecisionDefinitionOptimizeDto mapEngineEntityToOptimizeEntity(final DecisionDefinitionXmlEngineDto engineEntity) {
    final DecisionDefinitionOptimizeDto optimizeDto = new DecisionDefinitionOptimizeDto(
      engineEntity.getId(),
      engineEntity.getDmnXml(),
      engineContext.getEngineAlias()
    );
    return optimizeDto;
  }

}
