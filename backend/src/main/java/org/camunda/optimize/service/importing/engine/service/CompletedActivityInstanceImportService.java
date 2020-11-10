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
import org.camunda.optimize.service.es.writer.activity.CompletedActivityInstanceWriter;
import org.camunda.optimize.service.es.writer.usertask.CanceledUserTaskWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class CompletedActivityInstanceImportService implements ImportService<HistoricActivityInstanceEngineDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;
  private final CompletedActivityInstanceWriter completedActivityInstanceWriter;
  private final CamundaEventImportService camundaEventService;
  private final CanceledUserTaskWriter canceledUserTaskWriter;

  public CompletedActivityInstanceImportService(CanceledUserTaskWriter canceledUserTaskWriter,
                                                CompletedActivityInstanceWriter completedActivityInstanceWriter,
                                                CamundaEventImportService camundaEventService,
                                                ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                                EngineContext engineContext) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.completedActivityInstanceWriter = completedActivityInstanceWriter;
    this.camundaEventService = camundaEventService;
    this.canceledUserTaskWriter = canceledUserTaskWriter;
  }

  @Override
  public void executeImport(List<HistoricActivityInstanceEngineDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    logger.trace("Importing completed activity instances from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<FlowNodeEventDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<FlowNodeEventDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
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
      new CompletedActivityInstanceElasticsearchImportJob(
        completedActivityInstanceWriter,
        camundaEventService,
        canceledUserTaskWriter,
        callback
      );
    activityImportJob.setEntitiesToImport(events);
    return activityImportJob;
  }

  private FlowNodeEventDto mapEngineEntityToOptimizeEntity(final HistoricActivityInstanceEngineDto engineEntity) {
    return FlowNodeEventDto.builder()
      .id(engineEntity.getId())
      .activityId(engineEntity.getActivityId())
      .activityName(engineEntity.getActivityName())
      .timestamp(engineEntity.getStartTime())
      .processDefinitionKey(engineEntity.getProcessDefinitionKey())
      .processDefinitionId(engineEntity.getProcessDefinitionId())
      .processInstanceId(engineEntity.getProcessInstanceId())
      .startDate(engineEntity.getStartTime())
      .endDate(engineEntity.getEndTime())
      .durationInMs(engineEntity.getDurationInMillis())
      .activityType(engineEntity.getActivityType())
      .engineAlias(engineContext.getEngineAlias())
      .tenantId(engineEntity.getTenantId())
      .orderCounter(engineEntity.getSequenceCounter())
      .canceled(engineEntity.getCanceled())
      .taskId(engineEntity.getTaskId())
      .build();
  }

}
