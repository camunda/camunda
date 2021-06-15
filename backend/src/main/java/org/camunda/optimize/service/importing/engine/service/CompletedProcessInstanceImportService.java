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
import org.camunda.optimize.service.es.job.importing.CompletedProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;

import java.time.temporal.ChronoUnit;
import java.util.List;

public class CompletedProcessInstanceImportService extends AbstractProcessInstanceImportService {

  private final CompletedProcessInstanceWriter completedProcessInstanceWriter;
  private final CamundaEventImportService camundaEventService;

  public CompletedProcessInstanceImportService(final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                               final EngineContext engineContext,
                                               final BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider,
                                               final CompletedProcessInstanceWriter completedProcessInstanceWriter,
                                               final CamundaEventImportService camundaEventService) {
    super(elasticsearchImportJobExecutor, engineContext, businessKeyImportAdapterProvider);
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
    this.camundaEventService = camundaEventService;
  }


  @Override
  protected ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(
    final List<ProcessInstanceDto> processInstances,
    final Runnable callback) {
    CompletedProcessInstanceElasticsearchImportJob importJob = new CompletedProcessInstanceElasticsearchImportJob(
      completedProcessInstanceWriter, camundaEventService, callback);
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
      .endDate(engineEntity.getEndTime())
      .duration(engineEntity.getStartTime().until(engineEntity.getEndTime(), ChronoUnit.MILLIS))
      .state(engineEntity.getState())
      .dataSource(new EngineDataSourceDto(engineContext.getEngineAlias()))
      .tenantId(engineEntity.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null)))
      .build();
  }
}
