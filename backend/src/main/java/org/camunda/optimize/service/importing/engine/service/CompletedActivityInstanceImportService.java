/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.CompletedActivityInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.activity.CompletedActivityInstanceWriter;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class CompletedActivityInstanceImportService implements ImportService<HistoricActivityInstanceEngineDto> {

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected EngineContext engineContext;
  private final CompletedActivityInstanceWriter completedActivityInstanceWriter;
  private final CamundaEventImportService camundaEventService;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ConfigurationService configurationService;

  public CompletedActivityInstanceImportService(final CompletedActivityInstanceWriter completedActivityInstanceWriter,
                                                final CamundaEventImportService camundaEventService,
                                                final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                                final EngineContext engineContext,
                                                final ProcessDefinitionResolverService processDefinitionResolverService,
                                                final ConfigurationService configurationService) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.completedActivityInstanceWriter = completedActivityInstanceWriter;
    this.camundaEventService = camundaEventService;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.configurationService = configurationService;
  }

  @Override
  public void executeImport(List<HistoricActivityInstanceEngineDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    log.trace("Importing completed activity instances from engine...");

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

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob<?> elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<FlowNodeEventDto> mapEngineEntitiesToOptimizeEntities(
    final List<HistoricActivityInstanceEngineDto> engineEntities) {
    return engineEntities.stream()
      .map(this::mapEngineEntityToOptimizeEntity)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  private ElasticsearchImportJob<FlowNodeEventDto> createElasticsearchImportJob(List<FlowNodeEventDto> events,
                                                                                Runnable callback) {
    CompletedActivityInstanceElasticsearchImportJob activityImportJob =
      new CompletedActivityInstanceElasticsearchImportJob(
        completedActivityInstanceWriter,
        camundaEventService,
        configurationService,
        callback
      );
    activityImportJob.setEntitiesToImport(events);
    return activityImportJob;
  }

  private Optional<FlowNodeEventDto> mapEngineEntityToOptimizeEntity(final HistoricActivityInstanceEngineDto engineEntity) {
    return processDefinitionResolverService.getDefinition(engineEntity.getProcessDefinitionId(), engineContext)
      .map(definition -> new FlowNodeEventDto(
        engineEntity.getId(),
        engineEntity.getActivityId(),
        engineEntity.getActivityType(),
        engineEntity.getActivityName(),
        engineEntity.getStartTime(),
        definition.getId(),
        definition.getKey(),
        definition.getVersion(),
        definition.getTenantId(),
        engineContext.getEngineAlias(),
        engineEntity.getProcessInstanceId(),
        engineEntity.getStartTime(),
        engineEntity.getEndTime(),
        engineEntity.getDurationInMillis(),
        engineEntity.getSequenceCounter(),
        engineEntity.getCanceled(),
        engineEntity.getTaskId()
      ));
  }

}
