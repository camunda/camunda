/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;
import org.camunda.optimize.service.importing.engine.fetcher.instance.ProcessDefinitionXmlFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.ProcessDefinitionXmlEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ProcessDefinitionXmlEngineImportMediatorFactory extends AbstractImportMediatorFactory {
  private final ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  public ProcessDefinitionXmlEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                         final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                         final ConfigurationService configurationService,
                                                         final ProcessDefinitionXmlWriter processDefinitionXmlWriter) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;
  }

  public ProcessDefinitionXmlEngineImportMediator createProcessDefinitionXmlEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new ProcessDefinitionXmlEngineImportMediator(
      importIndexHandlerRegistry.getProcessDefinitionXmlImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(ProcessDefinitionXmlFetcher.class, engineContext),
      new ProcessDefinitionXmlImportService(
        elasticsearchImportJobExecutor,
        engineContext,
        processDefinitionXmlWriter
      ),
      configurationService,
      elasticsearchImportJobExecutor,
      new BackoffCalculator(configurationService)
    );
  }
}
