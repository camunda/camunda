/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.definition;

import static io.camunda.optimize.service.util.BpmnModelUtil.extractFlowNodeData;
import static io.camunda.optimize.service.util.BpmnModelUtil.extractUserTaskNames;
import static io.camunda.optimize.service.util.BpmnModelUtil.parseBpmnModel;

import io.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ProcessDefinitionXmlWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.ProcessDefinitionXmlDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

@Slf4j
public class ProcessDefinitionXmlImportService
    implements ImportService<ProcessDefinitionXmlEngineDto> {

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final EngineContext engineContext;
  private final ProcessDefinitionXmlWriter processDefinitionXmlWriter;
  private final DatabaseClient databaseClient;

  public ProcessDefinitionXmlImportService(
      final ConfigurationService configurationService,
      final EngineContext engineContext,
      final ProcessDefinitionXmlWriter processDefinitionXmlWriter,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.engineContext = engineContext;
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ProcessDefinitionXmlEngineDto> pageOfEngineEntities,
      final Runnable importCompleteCallback) {
    log.trace("Importing entities from engine...");

    final boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessDefinitionOptimizeDto> newOptimizeEntities =
          mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      final DatabaseImportJob<ProcessDefinitionOptimizeDto> databaseImportJob =
          createDatabaseImportJob(newOptimizeEntities, importCompleteCallback);
      addDatabaseImportJobToQueue(databaseImportJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private void addDatabaseImportJobToQueue(
      final DatabaseImportJob<ProcessDefinitionOptimizeDto> databaseImportJob) {
    databaseImportJobExecutor.executeImportJob(databaseImportJob);
  }

  private List<ProcessDefinitionOptimizeDto> mapEngineEntitiesToOptimizeEntities(
      final List<ProcessDefinitionXmlEngineDto> engineEntities) {
    return engineEntities.stream()
        .map(this::mapEngineEntityToOptimizeEntity)
        .collect(Collectors.toList());
  }

  private DatabaseImportJob<ProcessDefinitionOptimizeDto> createDatabaseImportJob(
      final List<ProcessDefinitionOptimizeDto> processDefinitions,
      final Runnable importCompleteCallback) {
    final ProcessDefinitionXmlDatabaseImportJob procDefImportJob =
        new ProcessDefinitionXmlDatabaseImportJob(
            processDefinitionXmlWriter, importCompleteCallback, databaseClient);
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  private ProcessDefinitionOptimizeDto mapEngineEntityToOptimizeEntity(
      final ProcessDefinitionXmlEngineDto engineEntity) {
    final BpmnModelInstance bpmnModelInstance = parseBpmnModel(engineEntity.getBpmn20Xml());
    return new ProcessDefinitionOptimizeDto(
        engineEntity.getId(),
        new EngineDataSourceDto(engineContext.getEngineAlias()),
        engineEntity.getBpmn20Xml(),
        extractFlowNodeData(bpmnModelInstance),
        extractUserTaskNames(bpmnModelInstance));
  }
}
