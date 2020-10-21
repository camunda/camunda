/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.definition.DecisionDefinitionFetcher;
import org.camunda.optimize.service.importing.engine.fetcher.instance.DecisionDefinitionXmlFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.DecisionDefinitionEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.DecisionDefinitionXmlEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionImportService;
import org.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionResolverService;
import org.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionXmlImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class DecisionDefinitionEngineImportMediatorFactory extends AbstractImportMediatorFactory {
  private final DecisionDefinitionWriter decisionDefinitionWriter;
  private final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;

  public DecisionDefinitionEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                       final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                       final ConfigurationService configurationService,
                                                       final DecisionDefinitionWriter decisionDefinitionWriter,
                                                       final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter,
                                                       final DecisionDefinitionResolverService decisionDefinitionResolverService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.decisionDefinitionWriter = decisionDefinitionWriter;
    this.decisionDefinitionXmlWriter = decisionDefinitionXmlWriter;
    this.decisionDefinitionResolverService = decisionDefinitionResolverService;
  }

  @Override
  public List<EngineImportMediator> createMediators(final EngineContext engineContext) {
    return configurationService.isImportDmnDataEnabled() ?
      ImmutableList.of(
        createDecisionDefinitionEngineImportMediator(engineContext),
        createDecisionDefinitionXmlEngineImportMediator(engineContext)
      )
      : Collections.emptyList();
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
        decisionDefinitionWriter,
        decisionDefinitionResolverService
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
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
