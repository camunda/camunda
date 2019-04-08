/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricDecisionInputInstanceDto;
import org.camunda.optimize.dto.engine.HistoricDecisionInstanceDto;
import org.camunda.optimize.dto.engine.HistoricDecisionOutputInstanceDto;
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
import org.camunda.optimize.service.exceptions.OptimizeDecisionDefinitionFetchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.ProcessVariableHelper.isVariableTypeSupported;

public class DecisionInstanceImportService implements ImportService<HistoricDecisionInstanceDto> {
  private static final Logger logger = LoggerFactory.getLogger(DecisionInstanceImportService.class);

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected String engineAlias;
  private DecisionInstanceWriter decisionInstanceWriter;
  private DecisionDefinitionVersionResolverService decisionDefinitionVersionResolverService;
  private DecisionInputImportAdapterProvider decisionInputImportAdapterProvider;
  private DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider;

  public DecisionInstanceImportService(DecisionInstanceWriter decisionInstanceWriter,
                                       ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                       EngineContext engineContext,
                                       DecisionDefinitionVersionResolverService
                                         decisionDefinitionVersionResolverService,
                                       DecisionInputImportAdapterProvider decisionInputImportAdapterProvider,
                                       DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineAlias = engineContext.getEngineAlias();
    this.decisionInstanceWriter = decisionInstanceWriter;
    this.decisionDefinitionVersionResolverService = decisionDefinitionVersionResolverService;
    this.decisionInputImportAdapterProvider = decisionInputImportAdapterProvider;
    this.decisionOutputImportAdapterProvider = decisionOutputImportAdapterProvider;
  }

  @Override
  public void executeImport(List<HistoricDecisionInstanceDto> engineDtoList, Runnable callback) {
    logger.trace("Importing entities from engine...");
    boolean newDataIsAvailable = !engineDtoList.isEmpty();

    if (newDataIsAvailable) {
      try {
        final List<DecisionInstanceDto> optimizeDtos = mapEngineEntitiesToOptimizeEntities(engineDtoList);

        final ElasticsearchImportJob<DecisionInstanceDto> elasticsearchImportJob = createElasticsearchImportJob(
          optimizeDtos, callback);
        addElasticsearchImportJobToQueue(elasticsearchImportJob);
      } catch (OptimizeDecisionDefinitionFetchException e) {
        logger.debug(
          "Required Decision Definition not imported yet, skipping current decision instance import cycle.",
          e.getMessage()
        );
      }
    }

  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<DecisionInstanceDto> mapEngineEntitiesToOptimizeEntities(List<HistoricDecisionInstanceDto>
                                                                          engineEntities)
    throws OptimizeDecisionDefinitionFetchException {
    List<DecisionInstanceDto> list = new ArrayList<>();
    for (HistoricDecisionInstanceDto engineEntity : engineEntities) {
      DecisionInstanceDto decisionInstanceDto = mapEngineEntityToOptimizeEntity(engineEntity);
      list.add(decisionInstanceDto);
    }
    return list;
  }

  private ElasticsearchImportJob<DecisionInstanceDto> createElasticsearchImportJob(List<DecisionInstanceDto>
                                                                                     decisionInstanceDtos,
                                                                                   Runnable callback) {
    final DecisionInstanceElasticsearchImportJob importJob = new DecisionInstanceElasticsearchImportJob(
      decisionInstanceWriter,
      callback
    );
    importJob.setEntitiesToImport(decisionInstanceDtos);
    return importJob;
  }

  public DecisionInstanceDto mapEngineEntityToOptimizeEntity(HistoricDecisionInstanceDto engineEntity)
    throws OptimizeDecisionDefinitionFetchException {
    DecisionInstanceDto decisionInstanceDto = new DecisionInstanceDto();
    decisionInstanceDto.setDecisionInstanceId(engineEntity.getId());
    decisionInstanceDto.setProcessDefinitionKey(engineEntity.getProcessDefinitionKey());
    decisionInstanceDto.setProcessDefinitionId(engineEntity.getProcessDefinitionId());
    decisionInstanceDto.setDecisionDefinitionId(engineEntity.getDecisionDefinitionId());
    decisionInstanceDto.setDecisionDefinitionKey(engineEntity.getDecisionDefinitionKey());
    decisionInstanceDto.setDecisionDefinitionVersion(resolveDecisionDefinitionVersion(engineEntity));
    decisionInstanceDto.setEvaluationDateTime(engineEntity.getEvaluationTime());
    decisionInstanceDto.setProcessInstanceId(engineEntity.getProcessInstanceId());
    decisionInstanceDto.setRootProcessInstanceId(engineEntity.getRootProcessInstanceId());
    decisionInstanceDto.setActivityId(engineEntity.getActivityId());
    decisionInstanceDto.setCollectResultValue(engineEntity.getCollectResultValue());
    decisionInstanceDto.setRootDecisionInstanceId(engineEntity.getRootDecisionInstanceId());

    prepareAndSetInputs(engineEntity, decisionInstanceDto);

    prepareAndSetOutputs(engineEntity, decisionInstanceDto);

    decisionInstanceDto.setMatchedRules(
      engineEntity.getOutputs().stream()
        .map(HistoricDecisionOutputInstanceDto::getRuleId)
        .collect(Collectors.toSet())
    );

    decisionInstanceDto.setEngine(engineAlias);
    return decisionInstanceDto;
  }

  private void prepareAndSetOutputs(HistoricDecisionInstanceDto engineEntity, DecisionInstanceDto decisionInstanceDto) {
    List<PluginDecisionOutputDto> outputInstanceDtoList = engineEntity.getOutputs()
      .stream()
      .map(o -> mapEngineOutputDtoToPluginOutputDto(decisionInstanceDto, o))
      .collect(Collectors.toList());

    for (DecisionOutputImportAdapter dmnInputImportAdapter : decisionOutputImportAdapterProvider.getPlugins()) {
      outputInstanceDtoList = dmnInputImportAdapter.adaptOutputs(outputInstanceDtoList);
    }

    List<OutputInstanceDto> outputList = outputInstanceDtoList.stream()
      .map(this::mapPluginOutputDtoToOptimizeOutputDto)
      .filter(this::isValidOutputInstanceDto)
      .collect(Collectors.toList());

    decisionInstanceDto.setOutputs(outputList);
  }

  private void prepareAndSetInputs(HistoricDecisionInstanceDto engineEntity, DecisionInstanceDto decisionInstanceDto) {
    List<PluginDecisionInputDto> inputInstanceDtoList = engineEntity.getInputs()
      .stream()
      .map(i -> mapEngineInputDtoToPluginInputDto(decisionInstanceDto, i))
      .collect(Collectors.toList());


    for (DecisionInputImportAdapter decisionInputImportAdapter : decisionInputImportAdapterProvider.getPlugins()) {
      inputInstanceDtoList = decisionInputImportAdapter.adaptInputs(inputInstanceDtoList);
    }

    List<InputInstanceDto> inputList = inputInstanceDtoList.stream()
      .map(this::mapPluginInputDtoToOptimizeInputDto)
      .filter(this::isValidInputInstanceDto)
      .collect(Collectors.toList());

    decisionInstanceDto.setInputs(inputList);
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

  private String resolveDecisionDefinitionVersion(final HistoricDecisionInstanceDto engineEntity) throws
                                                                                                  OptimizeDecisionDefinitionFetchException {
    return decisionDefinitionVersionResolverService
      .getVersionForDecisionDefinitionId(engineEntity.getDecisionDefinitionId())
      .orElseThrow(() -> {
        final String message = String.format(
          "Couldn't obtain version for decisionDefinitionId [%s]. It hasn't been imported yet",
          engineEntity.getDecisionDefinitionId()
        );
        return new OptimizeDecisionDefinitionFetchException(message);
      });
  }

  private PluginDecisionInputDto mapEngineInputDtoToPluginInputDto(DecisionInstanceDto decisionInstanceDto, final
  HistoricDecisionInputInstanceDto engineInputDto) {
    return new PluginDecisionInputDto(
      engineInputDto.getId(),
      engineInputDto.getClauseId(),
      engineInputDto.getClauseName(),
      engineInputDto.getType(),
      String.valueOf(engineInputDto.getValue()),
      decisionInstanceDto.getDecisionDefinitionKey(),
      decisionInstanceDto.getDecisionDefinitionVersion(),
      decisionInstanceDto.getDecisionDefinitionId(),
      decisionInstanceDto.getDecisionInstanceId(),
      engineAlias
    );
  }

  private PluginDecisionOutputDto mapEngineOutputDtoToPluginOutputDto(DecisionInstanceDto decisionInstanceDto, final
  HistoricDecisionOutputInstanceDto engineOutputDto) {
    return new PluginDecisionOutputDto(
      engineOutputDto.getId(),
      engineOutputDto.getClauseId(),
      engineOutputDto.getClauseName(),
      engineOutputDto.getRuleId(),
      engineOutputDto.getRuleOrder(),
      engineOutputDto.getVariableName(),
      engineOutputDto.getType(),
      String.valueOf(engineOutputDto.getValue()),
      decisionInstanceDto.getDecisionDefinitionKey(),
      decisionInstanceDto.getDecisionDefinitionVersion(),
      decisionInstanceDto.getDecisionDefinitionId(),
      decisionInstanceDto.getDecisionInstanceId(),
      engineAlias
    );
  }

  private boolean isValidInputInstanceDto(final InputInstanceDto inputInstanceDto) {
    if (!isVariableTypeSupported(inputInstanceDto.getType())) {
      logger.info(
        "Refuse to add input variable [{}] of type [{}]. Variable has no type or type is not supported.",
        inputInstanceDto.getId(),
        inputInstanceDto.getType()
      );
      return false;
    }
    return true;
  }


  private boolean isValidOutputInstanceDto(final OutputInstanceDto outputInstanceDto) {
    if (!isVariableTypeSupported(outputInstanceDto.getType())) {
      logger.info(
        "Refuse to add output variable [{}] of type [{}]. Variable has no type or type is not supported.",
        outputInstanceDto.getId(),
        outputInstanceDto.getType()
      );
      return false;
    }
    return true;
  }
}
