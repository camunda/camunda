/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.DecisionDefinitionXmlElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;
import org.camunda.optimize.service.exceptions.OptimizeDecisionDefinitionFetchException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.engine.importing.DmnModelUtility.extractInputVariables;
import static org.camunda.optimize.service.engine.importing.DmnModelUtility.extractOutputVariables;
import static org.camunda.optimize.service.engine.importing.DmnModelUtility.parseDmnModel;

@AllArgsConstructor
@Slf4j
public class DecisionDefinitionXmlImportService implements ImportService<DecisionDefinitionXmlEngineDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;

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
    final DmnModelInstance dmnModelInstance = parseDmnModel(engineEntity.getDmnXml());
    String decisionKey = resolveDecisionDefinitionKey(engineEntity);
    return new DecisionDefinitionOptimizeDto(
      engineEntity.getId(),
      engineContext.getEngineAlias(),
      engineEntity.getDmnXml(),
      extractInputVariables(dmnModelInstance, decisionKey),
      extractOutputVariables(dmnModelInstance, decisionKey)
    );
  }

  private String resolveDecisionDefinitionKey(final DecisionDefinitionXmlEngineDto engineEntity) {
    try {
      return decisionDefinitionResolverService
        .getKeyForDecisionDefinitionId(engineEntity.getId())
        .orElseThrow(() -> {
          final String message = String.format(
            "Couldn't obtain key for decisionDefinitionId [%s]. It hasn't been imported yet",
            engineEntity.getId()
          );
          return new OptimizeDecisionDefinitionFetchException(message);
        });
    } catch (OptimizeDecisionDefinitionFetchException e) {
      log.debug("Required Decision Definition not imported yet, skipping current decision xml import cycle.", e);
      throw new OptimizeRuntimeException(e.getMessage(), e);
    }
  }

}
