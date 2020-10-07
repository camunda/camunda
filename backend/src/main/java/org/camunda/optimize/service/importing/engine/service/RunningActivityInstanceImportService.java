/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.RunningActivityInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.activity.RunningActivityInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class RunningActivityInstanceImportService implements ImportService<HistoricActivityInstanceEngineDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;
  private final RunningActivityInstanceWriter runningActivityInstanceWriter;
  private final CamundaEventImportService camundaEventService;

  public RunningActivityInstanceImportService(RunningActivityInstanceWriter runningActivityInstanceWriter,
                                              CamundaEventImportService camundaEventService,
                                              ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                              EngineContext engineContext
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
    this.camundaEventService = camundaEventService;
  }

  @Override
  public void executeImport(List<HistoricActivityInstanceEngineDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    logger.trace("Importing running activity instances from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<FlowNodeEventDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<FlowNodeEventDto> elasticsearchImportJob =
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

  private List<FlowNodeEventDto> mapEngineEntitiesToOptimizeEntities(List<HistoricActivityInstanceEngineDto> engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<FlowNodeEventDto> createElasticsearchImportJob(List<FlowNodeEventDto> events,
                                                                                Runnable callback) {
    RunningActivityInstanceElasticsearchImportJob activityImportJob =
      new RunningActivityInstanceElasticsearchImportJob(runningActivityInstanceWriter, camundaEventService, callback);
    activityImportJob.setEntitiesToImport(events);
    return activityImportJob;
  }

  private FlowNodeEventDto mapEngineEntityToOptimizeEntity(HistoricActivityInstanceEngineDto engineEntity) {
    return FlowNodeEventDto.builder()
      .id(engineEntity.getId())
      .activityId(engineEntity.getActivityId())
      .activityName(engineEntity.getActivityName())
      .timestamp(engineEntity.getStartTime())
      .processDefinitionKey(engineEntity.getProcessDefinitionKey())
      .processDefinitionId(engineEntity.getProcessDefinitionId())
      .processInstanceId(engineEntity.getProcessInstanceId())
      .startDate(engineEntity.getStartTime())
      .activityType(engineEntity.getActivityType())
      .engineAlias(engineContext.getEngineAlias())
      .tenantId(engineEntity.getTenantId())
      .orderCounter(engineEntity.getSequenceCounter())
      .canceled(engineEntity.getCanceled())
      .taskId(engineEntity.getTaskId())
      .build();
  }

}
