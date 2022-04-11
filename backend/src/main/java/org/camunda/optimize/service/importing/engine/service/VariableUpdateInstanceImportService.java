/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricVariableUpdateInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableUpdateDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.plugin.VariableImportAdapterProvider;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.VariableUpdateElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.DateFormatterUtil.getDateStringInOptimizeDateFormat;
import static org.camunda.optimize.service.util.DateFormatterUtil.isValidOptimizeDateFormat;
import static org.camunda.optimize.service.util.VariableHelper.isProcessVariableTypeSupported;

@Slf4j
public class VariableUpdateInstanceImportService implements ImportService<HistoricVariableUpdateInstanceDto> {

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final VariableImportAdapterProvider variableImportAdapterProvider;
  private final ProcessVariableUpdateWriter variableWriter;
  private final CamundaEventImportService camundaEventService;
  private final EngineContext engineContext;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ConfigurationService configurationService;
  private final ObjectVariableService objectVariableService;

  public VariableUpdateInstanceImportService(final ConfigurationService configurationService,
                                             final VariableImportAdapterProvider variableImportAdapterProvider,
                                             final ProcessVariableUpdateWriter variableWriter,
                                             final CamundaEventImportService camundaEventService,
                                             final EngineContext engineContext,
                                             final ProcessDefinitionResolverService processDefinitionResolverService,
                                             final ObjectVariableService objectVariableService) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );

    this.variableImportAdapterProvider = variableImportAdapterProvider;
    this.variableWriter = variableWriter;
    this.camundaEventService = camundaEventService;
    this.engineContext = engineContext;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.configurationService = configurationService;
    this.objectVariableService = objectVariableService;
  }

  @Override
  public void executeImport(List<HistoricVariableUpdateInstanceDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    log.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessVariableDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<ProcessVariableDto> elasticsearchImportJob = createElasticsearchImportJob(
        newOptimizeEntities,
        importCompleteCallback
      );
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessVariableDto> mapEngineEntitiesToOptimizeEntities(
    List<HistoricVariableUpdateInstanceDto> engineEntities) {
    List<PluginVariableDto> pluginVariableList =
      mapEngineVariablesToOptimizeVariablesAndRemoveDuplicates(engineEntities);
    for (VariableImportAdapter variableImportAdapter : variableImportAdapterProvider.getPlugins()) {
      pluginVariableList = variableImportAdapter.adaptVariables(pluginVariableList);
    }
    pluginVariableList.removeIf(variable -> !isValidVariable(variable));
    List<ProcessVariableUpdateDto> variableUpdateDtos =
      pluginVariableList.stream().map(this::convertPluginVariableToImportVariable).collect(toList());
    return objectVariableService.convertObjectVariablesForImport(variableUpdateDtos);
  }

  private ProcessVariableUpdateDto convertPluginVariableToImportVariable(PluginVariableDto pluginVariableDto) {
    final ProcessVariableUpdateDto pluginVariable = new ProcessVariableUpdateDto(
      pluginVariableDto.getId(),
      pluginVariableDto.getName(),
      pluginVariableDto.getType(),
      pluginVariableDto.getValue(),
      pluginVariableDto.getTimestamp(),
      pluginVariableDto.getValueInfo(),
      pluginVariableDto.getProcessDefinitionKey(),
      pluginVariableDto.getProcessDefinitionId(),
      pluginVariableDto.getProcessInstanceId(),
      pluginVariableDto.getVersion(),
      pluginVariableDto.getEngineAlias(),
      pluginVariableDto.getTenantId()
    );
    normalizeDateVariableFormats(pluginVariable);
    return pluginVariable;
  }

  private List<PluginVariableDto> mapEngineVariablesToOptimizeVariablesAndRemoveDuplicates
    (List<HistoricVariableUpdateInstanceDto> engineEntities) {
    final Map<String, PluginVariableDto> resultSet = engineEntities.stream()
      .map(variable -> processDefinitionResolverService.enrichEngineDtoWithDefinitionKey(
        engineContext,
        variable,
        HistoricVariableUpdateInstanceDto::getProcessDefinitionKey,
        HistoricVariableUpdateInstanceDto::getProcessDefinitionId,
        HistoricVariableUpdateInstanceDto::setProcessDefinitionKey
      ))
      .filter(variable -> variable.getProcessDefinitionKey() != null)
      .map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toMap(
        PluginVariableDto::getId,
        pluginVariableDto -> pluginVariableDto,
        (existingEntry, newEntry) -> newEntry.getVersion() > existingEntry.getVersion() ? newEntry : existingEntry
      ));
    return new ArrayList<>(resultSet.values());
  }

  private PluginVariableDto mapEngineEntityToOptimizeEntity(HistoricVariableUpdateInstanceDto engineEntity) {
    return new PluginVariableDto(
      engineEntity.getVariableInstanceId(),
      engineEntity.getVariableName(),
      engineEntity.getVariableType(),
      engineEntity.getValue(),
      engineEntity.getTime(),
      engineEntity.getValueInfo(),
      engineEntity.getProcessDefinitionKey(),
      engineEntity.getProcessDefinitionId(),
      engineEntity.getProcessInstanceId(),
      engineEntity.getSequenceCounter(),
      engineContext.getEngineAlias(),
      engineEntity.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null))
    );
  }

  private boolean isValidVariable(PluginVariableDto variableDto) {
    if (variableDto == null) {
      log.info("Refuse to add null variable from import adapter plugin.");
      return false;
    } else if (isNullOrEmpty(variableDto.getId())) {
      log.info(
        "Refuse to add variable with name [{}] from variable import adapter plugin. Variable has no id.",
        variableDto.getName()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getName())) {
      log.info(
        "Refuse to add variable with id [{}] from variable import adapter plugin. Variable has no name.",
        variableDto.getId()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getType()) || !isProcessVariableTypeSupported(variableDto.getType())) {
      log.info(
        "Refuse to add variable [{}] with type [{}] from variable import adapter plugin. " +
          "Variable has no type or type is not supported.",
        variableDto.getName(),
        variableDto.getType()
      );
      return false;
    } else if (variableDto.getTimestamp() == null) {
      log.info(
        "Refuse to add variable [{}] from variable import adapter plugin. Variable has no timestamp.",
        variableDto.getName()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getProcessInstanceId())) {
      log.info(
        "Refuse to add variable [{}] from variable import adapter plugin. Variable has no process instance id.",
        variableDto.getName()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getProcessDefinitionId())) {
      log.info(
        "Refuse to add variable [{}] from variable import adapter plugin. Variable has no process definition id.",
        variableDto.getName()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getProcessDefinitionKey())) {
      log.info(
        "Refuse to add variable [{}] from variable import adapter plugin. Variable has no process definition key.",
        variableDto.getName()
      );
      return false;
    } else if (isNullOrZero(variableDto.getVersion())) {
      log.info(
        "Refuse to add variable [{}] with version [{}] from variable import adapter plugin. Variable has no version " +
          "or version is invalid.",
        variableDto.getName(),
        variableDto.getVersion()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getEngineAlias())) {
      log.info(
        "Refuse to add variable [{}] from variable import adapter plugin. Variable has no engine alias.",
        variableDto.getName()
      );
      return false;
    }
    return true;
  }

  private void normalizeDateVariableFormats(final ProcessVariableUpdateDto variable) {
    if (VariableType.DATE.getId().equalsIgnoreCase(variable.getType())) {
      final String dateVariableValue = variable.getValue();
      if (variable.getValue() != null && !isValidOptimizeDateFormat(variable.getValue())) {
        final Optional<String> optimizeDateFormat = getDateStringInOptimizeDateFormat(dateVariableValue);
        if (optimizeDateFormat.isPresent()) {
          variable.setValue(optimizeDateFormat.get());
        } else {
          log.trace(
            "Could not convert date variable with name {} and value {} to valid Optimize date format",
            variable.getName(),
            dateVariableValue
          );
        }
      }
    }
  }

  private boolean isNullOrEmpty(String value) {
    return value == null || value.isEmpty();
  }

  private boolean isNullOrZero(Long value) {
    return value == null || value.equals(0L);
  }

  private ElasticsearchImportJob<ProcessVariableDto> createElasticsearchImportJob(
    List<ProcessVariableDto> processVariables,
    Runnable callback) {
    VariableUpdateElasticsearchImportJob importJob = new VariableUpdateElasticsearchImportJob(
      variableWriter,
      camundaEventService,
      configurationService,
      callback
    );
    importJob.setEntitiesToImport(processVariables);
    return importJob;
  }

}
