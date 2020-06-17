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
      List<ProcessInstanceDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntitiesAndApplyPlugins(
        pageOfEngineEntities);
      ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
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

  private List<ProcessInstanceDto> mapEngineEntitiesToOptimizeEntitiesAndApplyPlugins(
    List<HistoricProcessInstanceDto> engineEntities) {
    return engineEntities.stream()
      .map(this::mapEngineEntityToOptimizeEntity)
      .peek(this::applyPlugins)
      .collect(toList());
  }

  private void applyPlugins(ProcessInstanceDto processInstanceDto) {
    businessKeyImportAdapterProvider.getPlugins()
      .forEach(businessKeyImportAdapter ->
                 processInstanceDto.setBusinessKey(
                   businessKeyImportAdapter.adaptBusinessKey(processInstanceDto.getBusinessKey())
                 )
      );
  }

  private ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(
    List<ProcessInstanceDto> processInstances,
    Runnable callback) {
    RunningProcessInstanceElasticsearchImportJob importJob =
      new RunningProcessInstanceElasticsearchImportJob(runningProcessInstanceWriter, camundaEventService, callback);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }


  private ProcessInstanceDto mapEngineEntityToOptimizeEntity(HistoricProcessInstanceDto engineEntity) {
    return new ProcessInstanceDto(
      engineEntity.getProcessDefinitionKey(),
      engineEntity.getProcessDefinitionVersionAsString(),
      engineEntity.getProcessDefinitionId(),
      engineEntity.getId(),
      engineEntity.getBusinessKey(),
      engineEntity.getStartTime(),
      null,
      null,
      engineEntity.getState(),
      engineContext.getEngineAlias(),
      engineEntity.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null))
    );
  }
}
