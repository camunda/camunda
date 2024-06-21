/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service;

import io.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import io.camunda.optimize.plugin.BusinessKeyImportAdapterProvider;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.CamundaEventImportService;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import io.camunda.optimize.service.db.writer.CompletedProcessInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import io.camunda.optimize.service.importing.job.CompletedProcessInstanceDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class CompletedProcessInstanceImportService extends AbstractProcessInstanceImportService {

  private final CompletedProcessInstanceWriter completedProcessInstanceWriter;
  private final CamundaEventImportService camundaEventService;
  private final ProcessInstanceRepository processInstanceRepository;

  public CompletedProcessInstanceImportService(
      final ConfigurationService configurationService,
      final EngineContext engineContext,
      final BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider,
      final CompletedProcessInstanceWriter completedProcessInstanceWriter,
      final CamundaEventImportService camundaEventService,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final DatabaseClient databaseClient,
      final ProcessInstanceRepository processInstanceRepository) {
    super(
        configurationService,
        engineContext,
        businessKeyImportAdapterProvider,
        processDefinitionResolverService,
        databaseClient);
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
    this.camundaEventService = camundaEventService;
    this.processInstanceRepository = processInstanceRepository;
  }

  @Override
  protected DatabaseImportJob<ProcessInstanceDto> createDatabaseImportJob(
      final List<ProcessInstanceDto> processInstances, final Runnable callback) {
    final CompletedProcessInstanceDatabaseImportJob importJob =
        new CompletedProcessInstanceDatabaseImportJob(
            completedProcessInstanceWriter,
            camundaEventService,
            callback,
            databaseClient,
            processInstanceRepository);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

  @Override
  protected ProcessInstanceDto mapEngineEntityToOptimizeEntity(
      final HistoricProcessInstanceDto engineEntity) {
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
        .tenantId(
            engineEntity
                .getTenantId()
                .orElseGet(() -> engineContext.getDefaultTenantId().orElse(null)))
        .build();
  }
}
