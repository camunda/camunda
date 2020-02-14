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
import org.camunda.optimize.service.es.job.importing.CompletedActivityInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.CompletedActivityInstanceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class CompletedActivityInstanceImportService implements ImportService<HistoricActivityInstanceEngineDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;
  private CompletedActivityInstanceWriter completedActivityInstanceWriter;
  private CamundaEventImportService camundaEventService;

  public CompletedActivityInstanceImportService(CompletedActivityInstanceWriter completedActivityInstanceWriter,
                                                CamundaEventImportService camundaEventService,
                                                ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                                EngineContext engineContext) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.completedActivityInstanceWriter = completedActivityInstanceWriter;
    this.camundaEventService = camundaEventService;
  }

  @Override
  public void executeImport(List<HistoricActivityInstanceEngineDto> pageOfEngineEntities, Runnable callback) {
    logger.trace("Importing completed activity instances from engine...");

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

  private List<FlowNodeEventDto> mapEngineEntitiesToOptimizeEntities(List<HistoricActivityInstanceEngineDto>
                                                                       engineEntities) {
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<FlowNodeEventDto> createElasticsearchImportJob(List<FlowNodeEventDto> events,
                                                                                Runnable callback) {
    CompletedActivityInstanceElasticsearchImportJob activityImportJob =
      new CompletedActivityInstanceElasticsearchImportJob(completedActivityInstanceWriter,
                                                          camundaEventService,
                                                          callback);
    activityImportJob.setEntitiesToImport(events);
    return activityImportJob;
  }

  private FlowNodeEventDto mapEngineEntityToOptimizeEntity(final HistoricActivityInstanceEngineDto engineEntity) {
    return new FlowNodeEventDto(
      engineEntity.getId(),
      engineEntity.getActivityId(),
      engineEntity.getActivityName(),
      engineEntity.getParentActivityInstanceId(),
      engineEntity.getStartTime(),
      engineEntity.getProcessDefinitionKey(),
      engineEntity.getProcessDefinitionId(),
      engineEntity.getProcessInstanceId(),
      engineEntity.getStartTime(),
      engineEntity.getEndTime(),
      engineEntity.getDurationInMillis(),
      engineEntity.getActivityType(),
      engineContext.getEngineAlias(),
      engineEntity.getTenantId()
    );
  }

}
