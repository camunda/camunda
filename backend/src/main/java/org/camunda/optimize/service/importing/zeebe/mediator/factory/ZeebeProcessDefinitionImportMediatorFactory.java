/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.zeebe.mediator.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.service.zeebe.ZeebeProcessDefinitionImportService;
import org.camunda.optimize.service.importing.zeebe.fetcher.ZeebeProcessDefinitionFetcher;
import org.camunda.optimize.service.importing.zeebe.mediator.ZeebeProcessDefinitionImportMediator;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ZeebeProcessDefinitionImportMediatorFactory extends AbstractZeebeImportMediatorFactory {

  private final ProcessDefinitionWriter processDefinitionWriter;

  public ZeebeProcessDefinitionImportMediatorFactory(final BeanFactory beanFactory,
                                                     final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                     final ConfigurationService configurationService,
                                                     final ProcessDefinitionWriter processDefinitionWriter,
                                                     final ObjectMapper objectMapper,
                                                     final OptimizeElasticsearchClient esClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, objectMapper, esClient);
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final int partitionId) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);
    return Collections.singletonList(
      new ZeebeProcessDefinitionImportMediator(
        importIndexHandlerRegistry.getZeebeProcessDefinitionImportIndexHandler(partitionId),
        beanFactory.getBean(
          ZeebeProcessDefinitionFetcher.class,
          partitionId,
          esClient,
          objectMapper,
          configurationService
        ),
        new ZeebeProcessDefinitionImportService(
          elasticsearchImportJobExecutor,
          processDefinitionWriter,
          configurationService.getConfiguredZeebe().getName(),
          partitionId
        ),
        configurationService,
        new BackoffCalculator(configurationService)
      )
    );
  }
}
