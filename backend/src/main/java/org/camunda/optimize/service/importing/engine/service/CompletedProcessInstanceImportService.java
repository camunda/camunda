/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.plugin.BusinessKeyImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.CompletedProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.time.temporal.ChronoUnit;
import java.util.List;

public class CompletedProcessInstanceImportService extends AbstractProcessInstanceImportService {

  private final CompletedProcessInstanceWriter completedProcessInstanceWriter;
  private final CamundaEventImportService camundaEventService;

  public CompletedProcessInstanceImportService(final ConfigurationService configurationService,
                                               final EngineContext engineContext,
                                               final BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider,
                                               final CompletedProcessInstanceWriter completedProcessInstanceWriter,
                                               final CamundaEventImportService camundaEventService,
                                               final ProcessDefinitionResolverService processDefinitionResolverService) {
    super(configurationService, engineContext, businessKeyImportAdapterProvider, processDefinitionResolverService);
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
    this.camundaEventService = camundaEventService;
  }


  @Override
  protected ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(
    final List<ProcessInstanceDto> processInstances,
    final Runnable callback) {
    CompletedProcessInstanceElasticsearchImportJob importJob = new CompletedProcessInstanceElasticsearchImportJob(
      completedProcessInstanceWriter, camundaEventService, configurationService, callback);
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
