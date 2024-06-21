/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.definition;

import static io.camunda.optimize.service.util.DmnModelUtil.extractInputVariables;
import static io.camunda.optimize.service.util.DmnModelUtil.extractOutputVariables;
import static io.camunda.optimize.service.util.DmnModelUtil.parseDmnModel;

import io.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.DecisionDefinitionXmlWriter;
import io.camunda.optimize.service.exceptions.OptimizeDecisionDefinitionNotFoundException;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.DecisionDefinitionXmlDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.dmn.DmnModelInstance;

@Slf4j
public class DecisionDefinitionXmlImportService
    implements ImportService<DecisionDefinitionXmlEngineDto> {

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final EngineContext engineContext;
  private final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;
  private final DatabaseClient databaseClient;

  public DecisionDefinitionXmlImportService(
      final ConfigurationService configurationService,
      final EngineContext engineContext,
      final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter,
      final DecisionDefinitionResolverService decisionDefinitionResolverService,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.engineContext = engineContext;
    this.decisionDefinitionXmlWriter = decisionDefinitionXmlWriter;
    this.decisionDefinitionResolverService = decisionDefinitionResolverService;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<DecisionDefinitionXmlEngineDto> engineDtoList,
      final Runnable importCompleteCallback) {
    log.trace("Importing entities from engine...");
    final boolean newDataIsAvailable = !engineDtoList.isEmpty();
    if (newDataIsAvailable) {
      final List<DecisionDefinitionOptimizeDto> optimizeDtos =
          mapEngineEntitiesToOptimizeEntities(engineDtoList);
      final DatabaseImportJob<DecisionDefinitionOptimizeDto> databaseImportJob =
          createDatabaseImportJob(optimizeDtos, importCompleteCallback);
      databaseImportJobExecutor.executeImportJob(databaseImportJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private List<DecisionDefinitionOptimizeDto> mapEngineEntitiesToOptimizeEntities(
      final List<DecisionDefinitionXmlEngineDto> engineEntities) {
    return engineEntities.stream()
        .map(this::mapEngineEntityToOptimizeEntity)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  private DatabaseImportJob<DecisionDefinitionOptimizeDto> createDatabaseImportJob(
      final List<DecisionDefinitionOptimizeDto> optimizeDtos,
      final Runnable importCompleteCallback) {
    final DecisionDefinitionXmlDatabaseImportJob importJob =
        new DecisionDefinitionXmlDatabaseImportJob(
            decisionDefinitionXmlWriter, importCompleteCallback, databaseClient);
    importJob.setEntitiesToImport(optimizeDtos);
    return importJob;
  }

  private Optional<DecisionDefinitionOptimizeDto> mapEngineEntityToOptimizeEntity(
      final DecisionDefinitionXmlEngineDto engineEntity) {
    final DmnModelInstance dmnModelInstance = parseDmnModel(engineEntity.getDmnXml());

    final Optional<String> definitionKey = resolveDecisionDefinitionKey(engineEntity);
    if (!definitionKey.isPresent()) {
      log.info(
          "Cannot retrieve definition key for definition with ID {}, skipping engine entity",
          engineEntity.getId());
      return Optional.empty();
    }
    return definitionKey.map(
        decisionKey ->
            new DecisionDefinitionOptimizeDto(
                engineEntity.getId(),
                new EngineDataSourceDto(engineContext.getEngineAlias()),
                engineEntity.getDmnXml(),
                extractInputVariables(dmnModelInstance, decisionKey),
                extractOutputVariables(dmnModelInstance, decisionKey)));
  }

  private Optional<String> resolveDecisionDefinitionKey(
      final DecisionDefinitionXmlEngineDto engineEntity) {
    try {
      return decisionDefinitionResolverService
          .getDefinition(engineEntity.getId(), engineContext)
          .map(DefinitionOptimizeResponseDto::getKey);
    } catch (final OptimizeDecisionDefinitionNotFoundException ex) {
      log.debug("Could not find the definition with ID {}", engineEntity.getId());
      return Optional.empty();
    }
  }
}
