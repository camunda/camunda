/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricVariableUpdateInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.VariableUpdateElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.VariableHelper.isVariableTypeSupported;

@Slf4j
public class VariableUpdateInstanceImportService implements ImportService<HistoricVariableUpdateInstanceDto> {

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private ImportAdapterProvider importAdapterProvider;
  protected EngineContext engineContext;
  private ProcessVariableUpdateWriter variableWriter;
  private CamundaEventService camundaEventService;

  public VariableUpdateInstanceImportService(
    ProcessVariableUpdateWriter variableWriter,
    CamundaEventService camundaEventService,
    ImportAdapterProvider importAdapterProvider,
    ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
    EngineContext engineContext
  ) {
    this.camundaEventService = camundaEventService;
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.variableWriter = variableWriter;
    this.importAdapterProvider = importAdapterProvider;
  }

  @Override
  public void executeImport(List<HistoricVariableUpdateInstanceDto> pageOfEngineEntities, Runnable callback) {
    log.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessVariableDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<ProcessVariableDto> elasticsearchImportJob = createElasticsearchImportJob(
        newOptimizeEntities,
        callback
      );
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessVariableDto> mapEngineEntitiesToOptimizeEntities(List<HistoricVariableUpdateInstanceDto>
                                                                  engineEntities) {
    List<PluginVariableDto> pluginVariableList = mapEngineVariablesToOptimizeVariablesAndRemoveDuplicates
      (engineEntities);
    for (VariableImportAdapter variableImportAdapter : importAdapterProvider.getPlugins()) {
      pluginVariableList = variableImportAdapter.adaptVariables(pluginVariableList);
    }
    return convertPluginListToImportList(pluginVariableList);
  }

  private List<ProcessVariableDto> convertPluginListToImportList(List<PluginVariableDto> pluginVariableList) {
    List<ProcessVariableDto> variableImportList = new ArrayList<>(pluginVariableList.size());
    for (PluginVariableDto dto : pluginVariableList) {
      if (dto != null && dto.getTimestamp() == null) {
        dto.setTimestamp(OffsetDateTime.now());
      }
      if (isValidVariable(dto)) {
        if (dto instanceof ProcessVariableDto) {
          variableImportList.add((ProcessVariableDto) dto);
        } else {
          variableImportList.add(convertPluginVariableToImportVariable(dto));
        }
      }
    }
    return variableImportList;
  }

  private ProcessVariableDto convertPluginVariableToImportVariable(PluginVariableDto pluginVariableDto) {
    return new ProcessVariableDto(
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
  }

  private List<PluginVariableDto> mapEngineVariablesToOptimizeVariablesAndRemoveDuplicates
    (List<HistoricVariableUpdateInstanceDto> engineEntities) {
    final Map<String, PluginVariableDto> resultSet = engineEntities.stream()
      .map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toMap(
        PluginVariableDto::getId,
        pluginVariableDto -> pluginVariableDto,
        (existingEntry, newEntry) -> newEntry.getVersion() > existingEntry.getVersion() ? newEntry : existingEntry
      ));
    return new ArrayList<>(resultSet.values());
  }

  private ProcessVariableDto mapEngineEntityToOptimizeEntity(HistoricVariableUpdateInstanceDto engineEntity) {
    return new ProcessVariableDto(
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
      engineEntity.getTenantId()
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
    } else if (isNullOrEmpty(variableDto.getType()) || !isVariableTypeSupported(variableDto.getType())) {
      log.info(
        "Refuse to add variable [{}] from variable import adapter plugin. Variable has no type or type is not " +
          "supported.",
        variableDto.getName()
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

  private boolean isNullOrEmpty(String value) {
    return value == null || value.isEmpty();
  }

  private boolean isNullOrZero(Long value) {
    return value == null || value.equals(0L);
  }

  private ElasticsearchImportJob<ProcessVariableDto> createElasticsearchImportJob(List<ProcessVariableDto> processInstances,
                                                                                  Runnable callback) {
    VariableUpdateElasticsearchImportJob importJob = new VariableUpdateElasticsearchImportJob(variableWriter,
                                                                                              camundaEventService, callback);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

}
