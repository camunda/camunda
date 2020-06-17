/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;
import org.camunda.optimize.service.importing.engine.fetcher.instance.DecisionDefinitionXmlFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.DecisionDefinitionXmlEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.DecisionDefinitionResolverService;
import org.camunda.optimize.service.importing.engine.service.DecisionDefinitionXmlImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class DecisionDefinitionXmlEngineImportMediatorFactory extends AbstractImportMediatorFactory {
  private final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;

  public DecisionDefinitionXmlEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                          final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                          final ConfigurationService configurationService,
                                                          final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter,
                                                          final DecisionDefinitionResolverService decisionDefinitionResolverService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.decisionDefinitionXmlWriter = decisionDefinitionXmlWriter;
    this.decisionDefinitionResolverService = decisionDefinitionResolverService;
  }

  public DecisionDefinitionXmlEngineImportMediator createDecisionDefinitionXmlEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new DecisionDefinitionXmlEngineImportMediator(
      importIndexHandlerRegistry.getDecisionDefinitionXmlImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(DecisionDefinitionXmlFetcher.class, engineContext),
      new DecisionDefinitionXmlImportService(
        elasticsearchImportJobExecutor,
        engineContext,
        decisionDefinitionXmlWriter,
        decisionDefinitionResolverService
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
