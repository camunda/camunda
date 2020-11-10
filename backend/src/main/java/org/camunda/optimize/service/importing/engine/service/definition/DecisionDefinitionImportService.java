/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service.definition;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.DecisionDefinitionElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;
import org.camunda.optimize.service.importing.engine.service.ImportService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@AllArgsConstructor
@Slf4j
public class DecisionDefinitionImportService implements ImportService<DecisionDefinitionEngineDto> {

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final DecisionDefinitionWriter decisionDefinitionWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;

  @Override
  public void executeImport(final List<DecisionDefinitionEngineDto> pageOfEngineEntities,
                            final Runnable importCompleteCallback) {
    log.trace("Importing decision definitions from engine...");
    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<DecisionDefinitionOptimizeDto> optimizeDtos = mapEngineEntitiesToOptimizeEntities(
        pageOfEngineEntities
      );
      markSavedDefinitionsAsDeleted(optimizeDtos);
      final ElasticsearchImportJob<DecisionDefinitionOptimizeDto> elasticsearchImportJob = createElasticsearchImportJob(
        optimizeDtos, importCompleteCallback
      );
      elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
    }
  }

  private void markSavedDefinitionsAsDeleted(final List<DecisionDefinitionOptimizeDto> definitionsToImport) {
    final boolean definitionsDeleted = decisionDefinitionWriter.markRedeployedDefinitionsAsDeleted(definitionsToImport);
    // We only resync the cache if at least one existing definition has been marked as deleted
    if (definitionsDeleted) {
      decisionDefinitionResolverService.syncCache();
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private List<DecisionDefinitionOptimizeDto> mapEngineEntitiesToOptimizeEntities(
    final List<DecisionDefinitionEngineDto> engineDtos) {
    // we mark new definitions as deleted if they are imported in the same batch as a newer deployment
    final Map<String, List<DecisionDefinitionEngineDto>> groupedDefinitions = engineDtos.stream()
      .collect(groupingBy(definition -> definition.getKey() + definition.getTenantId() + definition.getVersion()));
    groupedDefinitions.entrySet()
      .stream()
      .filter(entry -> entry.getValue().size() > 1)
      .forEach(entry -> {
        final DecisionDefinitionEngineDto newestDefinition = entry.getValue()
          .stream()
          .max(Comparator.comparing(DecisionDefinitionEngineDto::getDeploymentTime))
          .get();
        entry.getValue()
          .stream()
          .filter(definition -> !definition.equals(newestDefinition))
          .forEach(deletedDef -> deletedDef.setDeleted(true));
      });
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
    return DecisionDefinitionOptimizeDto.builder()
      .id(engineDto.getId())
      .key(engineDto.getKey())
      .version(String.valueOf(engineDto.getVersion()))
      .versionTag(engineDto.getVersionTag())
      .name(engineDto.getName())
      .engine(engineContext.getEngineAlias())
      .tenantId(engineDto.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null)))
      .deleted(engineDto.isDeleted())
      .build();
  }

}
