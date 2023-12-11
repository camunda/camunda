/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.definition;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.db.writer.ProcessDefinitionXmlWriter;
import org.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.importing.job.ProcessDefinitionXmlDatabaseImportJob;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.BpmnModelUtil.extractFlowNodeData;
import static org.camunda.optimize.service.util.BpmnModelUtil.extractUserTaskNames;
import static org.camunda.optimize.service.util.BpmnModelUtil.parseBpmnModel;

@Slf4j
public class ProcessDefinitionXmlImportService implements ImportService<ProcessDefinitionXmlEngineDto> {

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final EngineContext engineContext;
  private final ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  public ProcessDefinitionXmlImportService(final ConfigurationService configurationService,
                                           final EngineContext engineContext,
                                           final ProcessDefinitionXmlWriter processDefinitionXmlWriter) {
    this.databaseImportJobExecutor = new DatabaseImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.engineContext = engineContext;
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;
  }

  @Override
  public void executeImport(final List<ProcessDefinitionXmlEngineDto> pageOfEngineEntities,
                            final Runnable importCompleteCallback) {
    log.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
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
    return engineEntities
      .stream().map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toList());
  }

  private DatabaseImportJob<ProcessDefinitionOptimizeDto> createDatabaseImportJob(
    final List<ProcessDefinitionOptimizeDto> processDefinitions,
    final Runnable importCompleteCallback) {
    ProcessDefinitionXmlDatabaseImportJob procDefImportJob = new ProcessDefinitionXmlDatabaseImportJob(
      processDefinitionXmlWriter, importCompleteCallback
    );
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  private ProcessDefinitionOptimizeDto mapEngineEntityToOptimizeEntity(final ProcessDefinitionXmlEngineDto engineEntity) {
    final BpmnModelInstance bpmnModelInstance = parseBpmnModel(engineEntity.getBpmn20Xml());
    return new ProcessDefinitionOptimizeDto(
      engineEntity.getId(),
      new EngineDataSourceDto(engineContext.getEngineAlias()),
      engineEntity.getBpmn20Xml(),
      extractFlowNodeData(bpmnModelInstance),
      extractUserTaskNames(bpmnModelInstance)
    );
  }

}
