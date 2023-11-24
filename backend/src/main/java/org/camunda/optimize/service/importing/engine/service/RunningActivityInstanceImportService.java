/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.db.writer.activity.RunningActivityInstanceWriter;
import org.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.importing.job.RunningActivityInstanceDatabaseImportJob;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RunningActivityInstanceImportService implements ImportService<HistoricActivityInstanceEngineDto> {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected DatabaseImportJobExecutor databaseImportJobExecutor;
  protected EngineContext engineContext;
  private final RunningActivityInstanceWriter runningActivityInstanceWriter;
  private final CamundaEventImportService camundaEventService;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ConfigurationService configurationService;

  public RunningActivityInstanceImportService(final RunningActivityInstanceWriter runningActivityInstanceWriter,
                                              final CamundaEventImportService camundaEventService,
                                              final EngineContext engineContext,
                                              final ConfigurationService configurationService,
                                              final ProcessDefinitionResolverService processDefinitionResolverService) {
    this.databaseImportJobExecutor = new DatabaseImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.engineContext = engineContext;
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
    this.camundaEventService = camundaEventService;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.configurationService = configurationService;
  }

  @Override
  public void executeImport(List<HistoricActivityInstanceEngineDto> pageOfEngineEntities,
                            Runnable importCompleteCallback) {
    logger.trace("Importing running activity instances from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<FlowNodeEventDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      DatabaseImportJob<FlowNodeEventDto> databaseImportJob =
        createDatabaseImportJob(newOptimizeEntities, importCompleteCallback);
      addDatabaseImportJobToQueue(databaseImportJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private void addDatabaseImportJobToQueue(DatabaseImportJob databaseImportJob) {
    databaseImportJobExecutor.executeImportJob(databaseImportJob);
  }

  private List<FlowNodeEventDto> mapEngineEntitiesToOptimizeEntities(List<HistoricActivityInstanceEngineDto> engineEntities) {
    return engineEntities
      .stream()
      .map(activity -> processDefinitionResolverService.enrichEngineDtoWithDefinitionKey(
        engineContext,
        activity,
        HistoricActivityInstanceEngineDto::getProcessDefinitionKey,
        HistoricActivityInstanceEngineDto::getProcessDefinitionId,
        HistoricActivityInstanceEngineDto::setProcessDefinitionKey
      ))
      .filter(activity -> activity.getProcessDefinitionKey() != null)
      .map(this::mapEngineEntityToOptimizeEntity)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  private DatabaseImportJob<FlowNodeEventDto> createDatabaseImportJob(List<FlowNodeEventDto> events,
                                                                           Runnable callback) {
    RunningActivityInstanceDatabaseImportJob activityImportJob =
      new RunningActivityInstanceDatabaseImportJob(
        runningActivityInstanceWriter,
        camundaEventService,
        configurationService,
        callback
      );
    activityImportJob.setEntitiesToImport(events);
    return activityImportJob;
  }

  private Optional<FlowNodeEventDto> mapEngineEntityToOptimizeEntity(HistoricActivityInstanceEngineDto engineEntity) {
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
        engineEntity.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null)),
        engineContext.getEngineAlias(),
        engineEntity.getProcessInstanceId(),
        engineEntity.getStartTime(),
        null,
        null,
        engineEntity.getSequenceCounter(),
        engineEntity.getCanceled(),
        engineEntity.getTaskId()
      ));
  }

}
