/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.plugin.BusinessKeyImportAdapterProvider;
import org.camunda.optimize.plugin.importing.businesskey.BusinessKeyImportAdapter;
import org.camunda.optimize.plugin.importing.businesskey.PluginProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.RunningProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;

import java.util.List;

import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Slf4j
public class RunningProcessInstanceImportService implements ImportService<HistoricProcessInstanceDto> {

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;
  private BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider;
  private RunningProcessInstanceWriter runningProcessInstanceWriter;
  private CamundaEventImportService camundaEventService;

  @Override
  public void executeImport(List<HistoricProcessInstanceDto> pageOfEngineEntities, Runnable importCompleteCallback) {
    log.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessInstanceDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessInstanceDto> mapEngineEntitiesToOptimizeEntities(
    List<HistoricProcessInstanceDto> engineEntities) {
    List<PluginProcessInstanceDto> pluginProcessInstanceDtos = engineEntities.stream()
      .map(this::mapEngineEntityToOptimizePluginEntity)
      .collect(toList());
    for (BusinessKeyImportAdapter businessKeyImportAdapter : businessKeyImportAdapterProvider.getPlugins()) {
      pluginProcessInstanceDtos = businessKeyImportAdapter.adaptBusinessKeys(pluginProcessInstanceDtos);
    }
    return pluginProcessInstanceDtos
      .stream().map(this::mapPluginEntityToOptimizeEntity)
      .collect(toList());
  }

  private ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(
    List<ProcessInstanceDto> processInstances,
    Runnable callback) {
    RunningProcessInstanceElasticsearchImportJob importJob =
      new RunningProcessInstanceElasticsearchImportJob(runningProcessInstanceWriter, camundaEventService, callback);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

  private PluginProcessInstanceDto mapEngineEntityToOptimizePluginEntity(
    HistoricProcessInstanceDto engineEntity) {
    return new PluginProcessInstanceDto(
      engineEntity.getId(),
      engineEntity.getBusinessKey(),
      engineEntity.getProcessDefinitionId(),
      engineEntity.getProcessDefinitionKey(),
      engineEntity.getProcessDefinitionVersionAsString(),
      engineEntity.getProcessDefinitionName(),
      engineEntity.getStartTime(),
      null,
      engineEntity.getTenantId(),
      engineEntity.getState()
    );
  }

  private ProcessInstanceDto mapPluginEntityToOptimizeEntity(PluginProcessInstanceDto pluginProcessInstanceDto) {
    return new ProcessInstanceDto(
      pluginProcessInstanceDto.getProcessDefinitionKey(),
      pluginProcessInstanceDto.getProcessDefinitionVersion(),
      pluginProcessInstanceDto.getProcessDefinitionId(),
      pluginProcessInstanceDto.getId(),
      pluginProcessInstanceDto.getBusinessKey(),
      pluginProcessInstanceDto.getStartTime(),
      null,
      null,
      pluginProcessInstanceDto.getState(),
      engineContext.getEngineAlias(),
      pluginProcessInstanceDto.getTenantId() == null
        ? engineContext.getDefaultTenantId().orElse(null)
        : pluginProcessInstanceDto.getTenantId()
    );
  }

}
