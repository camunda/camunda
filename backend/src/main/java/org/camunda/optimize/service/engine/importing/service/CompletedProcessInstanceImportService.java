/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.CompletedProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

public class CompletedProcessInstanceImportService implements ImportService<HistoricProcessInstanceDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;
  private CompletedProcessInstanceWriter completedProcessInstanceWriter;

  public CompletedProcessInstanceImportService(CompletedProcessInstanceWriter completedProcessInstanceWriter,
                                               ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                               EngineContext engineContext
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
  }

  @Override
  public void executeImport(List<HistoricProcessInstanceDto> pageOfEngineEntities, Runnable callback) {
    logger.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessInstanceDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, callback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessInstanceDto> mapEngineEntitiesToOptimizeEntities(List<HistoricProcessInstanceDto>
                                                                         engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(List<ProcessInstanceDto> processInstances,
                                                                                  Runnable callback) {
    CompletedProcessInstanceElasticsearchImportJob importJob = new CompletedProcessInstanceElasticsearchImportJob(
      completedProcessInstanceWriter, callback);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

  private ProcessInstanceDto mapEngineEntityToOptimizeEntity(HistoricProcessInstanceDto engineEntity) {
    final ProcessInstanceDto processInstanceDto = new ProcessInstanceDto(
      engineEntity.getProcessDefinitionKey(),
      engineEntity.getProcessDefinitionVersionAsString(),
      engineEntity.getProcessDefinitionId(),
      engineEntity.getId(),
      engineEntity.getBusinessKey(),
      engineEntity.getStartTime(),
      engineEntity.getEndTime(),
      engineEntity.getStartTime().until(engineEntity.getEndTime(), ChronoUnit.MILLIS),
      engineEntity.getState(),
      engineContext.getEngineAlias(),
      engineEntity.getTenantId()
    );
    return processInstanceDto;
  }

}
