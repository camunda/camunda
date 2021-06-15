/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.plugin.BusinessKeyImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.RunningProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;

import java.util.List;

public class RunningProcessInstanceImportService extends AbstractProcessInstanceImportService {

  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private final CamundaEventImportService camundaEventService;

  public RunningProcessInstanceImportService(final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                             final EngineContext engineContext,
                                             final BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider,
                                             final RunningProcessInstanceWriter runningProcessInstanceWriter,
                                             final CamundaEventImportService camundaEventService) {
    super(elasticsearchImportJobExecutor, engineContext, businessKeyImportAdapterProvider);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.camundaEventService = camundaEventService;
  }

  @Override
  protected ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(
    final List<ProcessInstanceDto> processInstances,
    final Runnable callback) {
    RunningProcessInstanceElasticsearchImportJob importJob =
      new RunningProcessInstanceElasticsearchImportJob(runningProcessInstanceWriter, camundaEventService, callback);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

  @Override
  protected ProcessInstanceDto mapEngineEntityToOptimizeEntity(final HistoricProcessInstanceDto engineEntity) {
    return ProcessInstanceDto.builder()
      .processDefinitionKey(engineEntity.getProcessDefinitionKey())
      .processDefinitionVersion(engineEntity.getProcessDefinitionVersionAsString())
      .processDefinitionId(engineEntity.getProcessDefinitionId())
      .processInstanceId(engineEntity.getId())
      .businessKey(engineEntity.getBusinessKey())
      .startDate(engineEntity.getStartTime())
      .state(engineEntity.getState())
      .dataSource(new EngineDataSourceDto(engineContext.getEngineAlias()))
      .tenantId(engineEntity.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null)))
      .build();
  }
}
