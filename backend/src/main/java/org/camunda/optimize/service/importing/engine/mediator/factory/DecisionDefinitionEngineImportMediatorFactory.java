/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;
import org.camunda.optimize.service.importing.engine.fetcher.definition.DecisionDefinitionFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.DecisionDefinitionEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.DecisionDefinitionImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class DecisionDefinitionEngineImportMediatorFactory extends AbstractImportMediatorFactory {
  private final DecisionDefinitionWriter decisionDefinitionWriter;

  public DecisionDefinitionEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                       final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                       final ConfigurationService configurationService,
                                                       final DecisionDefinitionWriter decisionDefinitionWriter) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.decisionDefinitionWriter = decisionDefinitionWriter;
  }

  public DecisionDefinitionEngineImportMediator createDecisionDefinitionEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new DecisionDefinitionEngineImportMediator(
      importIndexHandlerRegistry.getDecisionDefinitionImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(DecisionDefinitionFetcher.class, engineContext),
      new DecisionDefinitionImportService(
        elasticsearchImportJobExecutor,
        engineContext,
        decisionDefinitionWriter
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
