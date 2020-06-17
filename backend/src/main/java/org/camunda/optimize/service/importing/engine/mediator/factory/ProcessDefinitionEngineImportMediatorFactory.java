/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.engine.fetcher.definition.ProcessDefinitionFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.ProcessDefinitionEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.ProcessDefinitionImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ProcessDefinitionEngineImportMediatorFactory extends AbstractImportMediatorFactory {
  private final ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionEngineImportMediatorFactory(final ProcessDefinitionWriter processDefinitionWriter,
                                                      final BeanFactory beanFactory,
                                                      final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                      final ConfigurationService configurationService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.processDefinitionWriter = processDefinitionWriter;
  }

  public ProcessDefinitionEngineImportMediator createProcessDefinitionEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new ProcessDefinitionEngineImportMediator(
      importIndexHandlerRegistry.getProcessDefinitionImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(ProcessDefinitionFetcher.class, engineContext),
      new ProcessDefinitionImportService(
        elasticsearchImportJobExecutor,
        engineContext,
        processDefinitionWriter
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
