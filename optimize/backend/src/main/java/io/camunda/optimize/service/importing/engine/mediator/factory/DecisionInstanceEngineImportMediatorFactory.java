/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.plugin.DecisionInputImportAdapterProvider;
import io.camunda.optimize.plugin.DecisionOutputImportAdapterProvider;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.DecisionInstanceWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.DecisionInstanceFetcher;
import io.camunda.optimize.service.importing.engine.mediator.DecisionInstanceEngineImportMediator;
import io.camunda.optimize.service.importing.engine.service.DecisionInstanceImportService;
import io.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionResolverService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class DecisionInstanceEngineImportMediatorFactory
    extends AbstractEngineImportMediatorFactory {
  private final DecisionInstanceWriter decisionInstanceWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;
  private final DecisionInputImportAdapterProvider decisionInputImportAdapterProvider;
  private final DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider;

  public DecisionInstanceEngineImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final DecisionInstanceWriter decisionInstanceWriter,
      final DecisionDefinitionResolverService decisionDefinitionResolverService,
      final DecisionInputImportAdapterProvider decisionInputImportAdapterProvider,
      final DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider,
      final DatabaseClient databaseClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, databaseClient);
    this.decisionInstanceWriter = decisionInstanceWriter;
    this.decisionDefinitionResolverService = decisionDefinitionResolverService;
    this.decisionInputImportAdapterProvider = decisionInputImportAdapterProvider;
    this.decisionOutputImportAdapterProvider = decisionOutputImportAdapterProvider;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return configurationService.isImportDmnDataEnabled()
        ? ImmutableList.of(createDecisionInstanceEngineImportMediator(engineContext))
        : Collections.emptyList();
  }

  public DecisionInstanceEngineImportMediator createDecisionInstanceEngineImportMediator(
      EngineContext engineContext) {
    return new DecisionInstanceEngineImportMediator(
        importIndexHandlerRegistry.getDecisionInstanceImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(DecisionInstanceFetcher.class, engineContext),
        new DecisionInstanceImportService(
            configurationService,
            engineContext,
            decisionInstanceWriter,
            decisionDefinitionResolverService,
            decisionInputImportAdapterProvider,
            decisionOutputImportAdapterProvider,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }
}
