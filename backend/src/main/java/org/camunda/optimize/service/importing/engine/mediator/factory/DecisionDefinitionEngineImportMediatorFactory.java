/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.DecisionDefinitionWriter;
import org.camunda.optimize.service.es.writer.DecisionDefinitionXmlWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.definition.DecisionDefinitionFetcher;
import org.camunda.optimize.service.importing.engine.fetcher.instance.DecisionDefinitionXmlFetcher;
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
public class DecisionDefinitionEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {
  private final DecisionDefinitionWriter decisionDefinitionWriter;
  private final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;

  public DecisionDefinitionEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                       final ImportIndexHandlerRegistry importIndexHandlerRegistry,
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
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return configurationService.isImportDmnDataEnabled() ?
      ImmutableList.of(
        createDecisionDefinitionEngineImportMediator(engineContext),
        createDecisionDefinitionXmlEngineImportMediator(engineContext)
      )
      : Collections.emptyList();
  }

  public DecisionDefinitionEngineImportMediator createDecisionDefinitionEngineImportMediator(
    EngineContext engineContext) {
    return new DecisionDefinitionEngineImportMediator(
      importIndexHandlerRegistry.getDecisionDefinitionImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(DecisionDefinitionFetcher.class, engineContext),
      new DecisionDefinitionImportService(
        configurationService,
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
    return new DecisionDefinitionXmlEngineImportMediator(
      importIndexHandlerRegistry.getDecisionDefinitionXmlImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(DecisionDefinitionXmlFetcher.class, engineContext, decisionDefinitionWriter),
      new DecisionDefinitionXmlImportService(
        configurationService,
        engineContext,
        decisionDefinitionXmlWriter,
        decisionDefinitionResolverService
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
