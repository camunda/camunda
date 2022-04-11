/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricDecisionInputInstanceDto;
import org.camunda.optimize.dto.engine.HistoricDecisionInstanceDto;
import org.camunda.optimize.dto.engine.HistoricDecisionOutputInstanceDto;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.plugin.DecisionInputImportAdapterProvider;
import org.camunda.optimize.plugin.DecisionOutputImportAdapterProvider;
import org.camunda.optimize.plugin.importing.variable.DecisionInputImportAdapter;
import org.camunda.optimize.plugin.importing.variable.DecisionOutputImportAdapter;
import org.camunda.optimize.plugin.importing.variable.PluginDecisionInputDto;
import org.camunda.optimize.plugin.importing.variable.PluginDecisionOutputDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.DecisionInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeDecisionDefinitionNotFoundException;
import org.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.VariableHelper.isDecisionVariableTypeSupported;

@Slf4j
public class DecisionInstanceImportService implements ImportService<HistoricDecisionInstanceDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final DecisionInstanceWriter decisionInstanceWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;
  private final DecisionInputImportAdapterProvider decisionInputImportAdapterProvider;
  private final DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider;

  public DecisionInstanceImportService(final ConfigurationService configurationService,
                                       final EngineContext engineContext,
                                       final DecisionInstanceWriter decisionInstanceWriter,
                                       final DecisionDefinitionResolverService decisionDefinitionResolverService,
                                       final DecisionInputImportAdapterProvider decisionInputImportAdapterProvider,
                                       final DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.engineContext = engineContext;
    this.decisionInstanceWriter = decisionInstanceWriter;
    this.decisionDefinitionResolverService = decisionDefinitionResolverService;
    this.decisionInputImportAdapterProvider = decisionInputImportAdapterProvider;
    this.decisionOutputImportAdapterProvider = decisionOutputImportAdapterProvider;
  }

  @Override
  public void executeImport(List<HistoricDecisionInstanceDto> engineDtoList, Runnable importCompleteCallback) {
    log.trace("Importing entities from engine...");
    boolean newDataIsAvailable = !engineDtoList.isEmpty();

    if (newDataIsAvailable) {
      final List<DecisionInstanceDto> optimizeDtos = mapEngineEntitiesToOptimizeEntities(engineDtoList);

      final ElasticsearchImportJob<DecisionInstanceDto> elasticsearchImportJob = createElasticsearchImportJob(
        optimizeDtos, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  public Optional<DecisionInstanceDto> mapEngineEntityToOptimizeEntity(HistoricDecisionInstanceDto engineEntity) {
    final Optional<DecisionDefinitionOptimizeDto> definition = resolveDecisionDefinition(engineEntity);
    if (!definition.isPresent()) {
      log.info("Cannot retrieve definition for definition with ID {}. Skipping import of decision instance with ID {}",
               engineEntity.getDecisionDefinitionId(), engineEntity.getId()
      );
      return Optional.empty();
    }
    final DecisionDefinitionOptimizeDto resolvedDefinition = definition.get();
    return Optional.of(new DecisionInstanceDto(
      engineEntity.getId(),
      engineEntity.getProcessDefinitionId(),
      engineEntity.getProcessDefinitionKey(),
      engineEntity.getDecisionDefinitionId(),
      engineEntity.getDecisionDefinitionKey(),
      resolvedDefinition.getVersion(),
      engineEntity.getEvaluationTime(),
      engineEntity.getProcessInstanceId(),
      engineEntity.getRootProcessInstanceId(),
      engineEntity.getActivityId(),
      engineEntity.getCollectResultValue(),
      engineEntity.getRootDecisionInstanceId(),
      mapDecisionInputs(engineEntity, resolvedDefinition),
      mapDecisionOutputs(engineEntity, resolvedDefinition),
      engineEntity.getOutputs().stream()
        .map(HistoricDecisionOutputInstanceDto::getRuleId)
        .collect(Collectors.toSet()),
      engineContext.getEngineAlias(),
      engineEntity.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null))
    ));
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<DecisionInstanceDto> mapEngineEntitiesToOptimizeEntities(List<HistoricDecisionInstanceDto> engineEntities) {
    return engineEntities.stream()
      .map(this::mapEngineEntityToOptimizeEntity)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<DecisionInstanceDto> createElasticsearchImportJob(List<DecisionInstanceDto> decisionInstanceDtos,
                                                                                   Runnable callback) {
    final DecisionInstanceElasticsearchImportJob importJob = new DecisionInstanceElasticsearchImportJob(
      decisionInstanceWriter,
      callback
    );
    importJob.setEntitiesToImport(decisionInstanceDtos);
    return importJob;
  }

  private List<OutputInstanceDto> mapDecisionOutputs(HistoricDecisionInstanceDto engineEntity,
                                                     final DecisionDefinitionOptimizeDto resolvedDefinition) {
    List<PluginDecisionOutputDto> outputInstanceDtoList = engineEntity.getOutputs()
      .stream()
      .map(o -> mapEngineOutputDtoToPluginOutputDto(engineEntity, o, resolvedDefinition))
      .collect(Collectors.toList());

    for (DecisionOutputImportAdapter dmnInputImportAdapter : decisionOutputImportAdapterProvider.getPlugins()) {
      outputInstanceDtoList = dmnInputImportAdapter.adaptOutputs(outputInstanceDtoList);
    }

    return outputInstanceDtoList.stream()
      .filter(this::isValidOutputInstanceDto)
      .map(this::mapPluginOutputDtoToOptimizeOutputDto)
      .collect(Collectors.toList());
  }

  private List<InputInstanceDto> mapDecisionInputs(HistoricDecisionInstanceDto engineEntity,
                                                   final DecisionDefinitionOptimizeDto resolvedDefinition) {
    List<PluginDecisionInputDto> inputInstanceDtoList = engineEntity.getInputs()
      .stream()
      .map(i -> mapEngineInputDtoToPluginInputDto(engineEntity, i, resolvedDefinition))
      .collect(Collectors.toList());


    for (DecisionInputImportAdapter decisionInputImportAdapter : decisionInputImportAdapterProvider.getPlugins()) {
      inputInstanceDtoList = decisionInputImportAdapter.adaptInputs(inputInstanceDtoList);
    }

    return inputInstanceDtoList.stream()
      .filter(this::isValidInputInstanceDto)
      .map(this::mapPluginInputDtoToOptimizeInputDto)
      .collect(Collectors.toList());
  }

  private InputInstanceDto mapPluginInputDtoToOptimizeInputDto(PluginDecisionInputDto pluginDecisionInputDto) {
    return new InputInstanceDto(
      pluginDecisionInputDto.getId(),
      pluginDecisionInputDto.getClauseId(),
      pluginDecisionInputDto.getClauseName(),
      Optional.ofNullable(pluginDecisionInputDto.getType()).map(VariableType::getTypeForId).orElse(null),
      pluginDecisionInputDto.getValue()
    );
  }

  private OutputInstanceDto mapPluginOutputDtoToOptimizeOutputDto(PluginDecisionOutputDto pluginDecisionOutputDto) {
    return new OutputInstanceDto(
      pluginDecisionOutputDto.getId(),
      pluginDecisionOutputDto.getClauseId(),
      pluginDecisionOutputDto.getClauseName(),
      pluginDecisionOutputDto.getRuleId(),
      pluginDecisionOutputDto.getRuleOrder(),
      pluginDecisionOutputDto.getVariableName(),
      Optional.ofNullable(pluginDecisionOutputDto.getType()).map(VariableType::getTypeForId).orElse(null),
      pluginDecisionOutputDto.getValue()
    );
  }

  @SneakyThrows
  private PluginDecisionInputDto mapEngineInputDtoToPluginInputDto(final HistoricDecisionInstanceDto decisionInstanceDto,
                                                                   final HistoricDecisionInputInstanceDto engineInputDto,
                                                                   final DecisionDefinitionOptimizeDto resolvedDefinition) {
    return new PluginDecisionInputDto(
      engineInputDto.getId(),
      engineInputDto.getClauseId(),
      engineInputDto.getClauseName(),
      engineInputDto.getType(),
      Optional.ofNullable(engineInputDto.getValue()).map(String::valueOf).orElse(null),
      decisionInstanceDto.getDecisionDefinitionKey(),
      resolvedDefinition.getVersion(),
      decisionInstanceDto.getDecisionDefinitionId(),
      decisionInstanceDto.getId(),
      engineContext.getEngineAlias(),
      decisionInstanceDto.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null))
    );
  }

  @SneakyThrows
  private PluginDecisionOutputDto mapEngineOutputDtoToPluginOutputDto(final HistoricDecisionInstanceDto decisionInstanceDto,
                                                                      final HistoricDecisionOutputInstanceDto engineOutputDto,
                                                                      final DecisionDefinitionOptimizeDto resolvedDefinition) {
    return new PluginDecisionOutputDto(
      engineOutputDto.getId(),
      engineOutputDto.getClauseId(),
      engineOutputDto.getClauseName(),
      engineOutputDto.getRuleId(),
      engineOutputDto.getRuleOrder(),
      engineOutputDto.getVariableName(),
      engineOutputDto.getType(),
      Optional.ofNullable(engineOutputDto.getValue()).map(String::valueOf).orElse(null),
      decisionInstanceDto.getDecisionDefinitionKey(),
      resolvedDefinition.getVersion(),
      decisionInstanceDto.getDecisionDefinitionId(),
      decisionInstanceDto.getId(),
      engineContext.getEngineAlias(),
      decisionInstanceDto.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null))
    );
  }

  private boolean isValidInputInstanceDto(final PluginDecisionInputDto inputInstanceDto) {
    if (!isDecisionVariableTypeSupported(inputInstanceDto.getType())) {
      log.info(
        "Refuse to add input variable [id: {}, clauseId: {}, clauseName: {}, type: {}] " +
          "for decision instance with id [{}]. Variable has no type or type is not supported.",
        inputInstanceDto.getId(),
        inputInstanceDto.getClauseId(),
        inputInstanceDto.getClauseName(),
        inputInstanceDto.getType(),
        inputInstanceDto.getDecisionInstanceId()
      );
      return false;
    }
    return true;
  }


  private boolean isValidOutputInstanceDto(final PluginDecisionOutputDto outputInstanceDto) {
    if (!isDecisionVariableTypeSupported(outputInstanceDto.getType())) {
      log.info(
        "Refuse to add output variable [id: {}, clauseId: {}, clauseName: {}, type: {}] " +
          "for decision instance with id [{}]. Variable has no type or type is not supported.",
        outputInstanceDto.getId(),
        outputInstanceDto.getClauseId(),
        outputInstanceDto.getClauseName(),
        outputInstanceDto.getType(),
        outputInstanceDto.getDecisionInstanceId()
      );
      return false;
    }
    return true;
  }

  private Optional<DecisionDefinitionOptimizeDto> resolveDecisionDefinition(final HistoricDecisionInstanceDto engineEntity) {
    try {
      return decisionDefinitionResolverService.getDefinition(engineEntity.getDecisionDefinitionId(), engineContext);
    } catch (OptimizeDecisionDefinitionNotFoundException ex) {
      log.debug("Could not find the definition with ID {}", engineEntity.getDecisionDefinitionId());
      return Optional.empty();
    }
  }

}
