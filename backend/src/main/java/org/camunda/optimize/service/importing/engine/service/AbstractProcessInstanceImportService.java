/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.plugin.BusinessKeyImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.stream.Collectors.toList;

public abstract class AbstractProcessInstanceImportService implements ImportService<HistoricProcessInstanceDto> {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  protected final EngineContext engineContext;
  protected final BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  protected final ConfigurationService configurationService;

  public AbstractProcessInstanceImportService(final ConfigurationService configurationService,
                                              final EngineContext engineContext,
                                              final BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider,
                                              final ProcessDefinitionResolverService processDefinitionResolverService) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.engineContext = engineContext;
    this.businessKeyImportAdapterProvider = businessKeyImportAdapterProvider;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.configurationService = configurationService;
  }

  protected abstract ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(
    List<ProcessInstanceDto> processInstances,
    Runnable callback
  );

  protected abstract ProcessInstanceDto mapEngineEntityToOptimizeEntity(HistoricProcessInstanceDto engineEntity);

  @Override
  public void executeImport(final List<HistoricProcessInstanceDto> pageOfEngineEntities,
                            final Runnable importCompleteCallback) {
    log.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<ProcessInstanceDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntitiesAndApplyPlugins(
        pageOfEngineEntities);
      ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(final ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessInstanceDto> mapEngineEntitiesToOptimizeEntitiesAndApplyPlugins(
    final List<HistoricProcessInstanceDto> engineEntities) {
    return engineEntities.stream()
      .map(instance -> processDefinitionResolverService.enrichEngineDtoWithDefinitionKey(
        engineContext,
        instance,
        HistoricProcessInstanceDto::getProcessDefinitionKey,
        HistoricProcessInstanceDto::getProcessDefinitionId,
        HistoricProcessInstanceDto::setProcessDefinitionKey
      ))
      .filter(instance -> instance.getProcessDefinitionKey() != null)
      .map(this::mapEngineEntityToOptimizeEntity)
      .map(this::applyPlugins)
      .collect(toList());
  }

  private ProcessInstanceDto applyPlugins(final ProcessInstanceDto processInstanceDto) {
    businessKeyImportAdapterProvider.getPlugins()
      .forEach(businessKeyImportAdapter -> processInstanceDto.setBusinessKey(
        businessKeyImportAdapter.adaptBusinessKey(processInstanceDto.getBusinessKey())
      ));
    return processInstanceDto;
  }
}
