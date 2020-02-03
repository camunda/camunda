/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaActivityEventService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.RunningActivityInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningActivityInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class RunningActivityInstanceImportService implements ImportService<HistoricActivityInstanceEngineDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;
  private RunningActivityInstanceWriter runningActivityInstanceWriter;
  private CamundaActivityEventService camundaActivityEventService;

  public RunningActivityInstanceImportService(RunningActivityInstanceWriter runningActivityInstanceWriter,
                                              CamundaActivityEventService camundaActivityEventService,
                                              ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                              EngineContext engineContext
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
    this.camundaActivityEventService = camundaActivityEventService;
  }

  @Override
  public void executeImport(List<HistoricActivityInstanceEngineDto> pageOfEngineEntities, Runnable callback) {
    logger.trace("Importing running activity instances from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<FlowNodeEventDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<FlowNodeEventDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, callback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<FlowNodeEventDto> mapEngineEntitiesToOptimizeEntities(List<HistoricActivityInstanceEngineDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<FlowNodeEventDto> createElasticsearchImportJob(List<FlowNodeEventDto> events,
                                                                                Runnable callback) {
    RunningActivityInstanceElasticsearchImportJob activityImportJob =
      new RunningActivityInstanceElasticsearchImportJob(runningActivityInstanceWriter, camundaActivityEventService, callback);
    activityImportJob.setEntitiesToImport(events);
    return activityImportJob;
  }

  private FlowNodeEventDto mapEngineEntityToOptimizeEntity(HistoricActivityInstanceEngineDto engineEntity) {
    final FlowNodeEventDto flowNodeEventDto = new FlowNodeEventDto(
      engineEntity.getId(),
      engineEntity.getActivityId(),
      engineEntity.getActivityName(),
      engineEntity.getParentActivityInstanceId(),
      engineEntity.getStartTime(),
      engineEntity.getProcessDefinitionKey(),
      engineEntity.getProcessDefinitionId(),
      engineEntity.getProcessInstanceId(),
      engineEntity.getStartTime(),
      null,
      null,
      engineEntity.getActivityType(),
      engineContext.getEngineAlias(),
      engineEntity.getTenantId()
    );
    return flowNodeEventDto;
  }

}
