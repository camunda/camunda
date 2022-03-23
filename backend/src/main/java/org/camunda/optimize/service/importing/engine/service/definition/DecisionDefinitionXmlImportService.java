/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.definition;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.DecisionDefinitionXmlElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;
import org.camunda.optimize.service.exceptions.OptimizeDecisionDefinitionNotFoundException;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.DmnModelUtil.extractInputVariables;
import static org.camunda.optimize.service.util.DmnModelUtil.extractOutputVariables;
import static org.camunda.optimize.service.util.DmnModelUtil.parseDmnModel;

@Slf4j
public class DecisionDefinitionXmlImportService implements ImportService<DecisionDefinitionXmlEngineDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;

  public DecisionDefinitionXmlImportService(final ConfigurationService configurationService,
                                            final EngineContext engineContext,
                                            final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter,
                                            final DecisionDefinitionResolverService decisionDefinitionResolverService) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.engineContext = engineContext;
    this.decisionDefinitionXmlWriter = decisionDefinitionXmlWriter;
    this.decisionDefinitionResolverService = decisionDefinitionResolverService;
  }

  @Override
  public void executeImport(final List<DecisionDefinitionXmlEngineDto> engineDtoList,
                            final Runnable importCompleteCallback) {
    log.trace("Importing entities from engine...");
    final boolean newDataIsAvailable = !engineDtoList.isEmpty();
    if (newDataIsAvailable) {
      final List<DecisionDefinitionOptimizeDto> optimizeDtos = mapEngineEntitiesToOptimizeEntities(engineDtoList);
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
    final List<DecisionDefinitionXmlEngineDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<DecisionDefinitionOptimizeDto> createElasticsearchImportJob(
    final List<DecisionDefinitionOptimizeDto> optimizeDtos,
    final Runnable importCompleteCallback) {
    DecisionDefinitionXmlElasticsearchImportJob importJob = new DecisionDefinitionXmlElasticsearchImportJob(
      decisionDefinitionXmlWriter, importCompleteCallback
    );
    importJob.setEntitiesToImport(optimizeDtos);
    return importJob;
  }

  private Optional<DecisionDefinitionOptimizeDto> mapEngineEntityToOptimizeEntity(final DecisionDefinitionXmlEngineDto engineEntity) {
    final DmnModelInstance dmnModelInstance = parseDmnModel(engineEntity.getDmnXml());

    final Optional<String> definitionKey = resolveDecisionDefinitionKey(engineEntity);
    if (!definitionKey.isPresent()) {
      log.info(
        "Cannot retrieve definition key for definition with ID {}, skipping engine entity",
        engineEntity.getId()
      );
      return Optional.empty();
    }
    return definitionKey.map(
      decisionKey -> new DecisionDefinitionOptimizeDto(
        engineEntity.getId(),
        new EngineDataSourceDto(engineContext.getEngineAlias()),
        engineEntity.getDmnXml(),
        extractInputVariables(dmnModelInstance, decisionKey),
        extractOutputVariables(dmnModelInstance, decisionKey)
      ));
  }

  private Optional<String> resolveDecisionDefinitionKey(final DecisionDefinitionXmlEngineDto engineEntity) {
    try {
      return decisionDefinitionResolverService.getDefinition(engineEntity.getId(), engineContext)
        .map(DefinitionOptimizeResponseDto::getKey);
    } catch (OptimizeDecisionDefinitionNotFoundException ex) {
      log.debug("Could not find the definition with ID {}", engineEntity.getId());
      return Optional.empty();
    }
  }

}
