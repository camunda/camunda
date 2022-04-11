/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.plugin.DecisionInputImportAdapterProvider;
import org.camunda.optimize.plugin.DecisionOutputImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.DecisionInstanceFetcher;
import org.camunda.optimize.service.importing.engine.mediator.DecisionInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.DecisionInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionResolverService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class DecisionInstanceEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {
  private final DecisionInstanceWriter decisionInstanceWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;
  private final DecisionInputImportAdapterProvider decisionInputImportAdapterProvider;
  private final DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider;

  public DecisionInstanceEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                     final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                     final ConfigurationService configurationService,
                                                     final DecisionInstanceWriter decisionInstanceWriter,
                                                     final DecisionDefinitionResolverService decisionDefinitionResolverService,
                                                     final DecisionInputImportAdapterProvider decisionInputImportAdapterProvider,
                                                     final DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.decisionInstanceWriter = decisionInstanceWriter;
    this.decisionDefinitionResolverService = decisionDefinitionResolverService;
    this.decisionInputImportAdapterProvider = decisionInputImportAdapterProvider;
    this.decisionOutputImportAdapterProvider = decisionOutputImportAdapterProvider;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return configurationService.isImportDmnDataEnabled() ?
      ImmutableList.of(createDecisionInstanceEngineImportMediator(engineContext))
      : Collections.emptyList();
  }

  public DecisionInstanceEngineImportMediator createDecisionInstanceEngineImportMediator(
    EngineContext engineContext) {
    return new DecisionInstanceEngineImportMediator(
      importIndexHandlerRegistry.getDecisionInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(DecisionInstanceFetcher.class, engineContext),
      new DecisionInstanceImportService(
        configurationService,
        engineContext,
        decisionInstanceWriter,
        decisionDefinitionResolverService,
        decisionInputImportAdapterProvider,
        decisionOutputImportAdapterProvider
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

}
