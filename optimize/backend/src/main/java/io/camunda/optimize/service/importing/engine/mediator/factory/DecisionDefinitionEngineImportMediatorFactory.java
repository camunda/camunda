/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.DecisionDefinitionWriter;
import io.camunda.optimize.service.db.writer.DecisionDefinitionXmlWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.definition.DecisionDefinitionFetcher;
import io.camunda.optimize.service.importing.engine.fetcher.instance.DecisionDefinitionXmlFetcher;
import io.camunda.optimize.service.importing.engine.mediator.DecisionDefinitionEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.DecisionDefinitionXmlEngineImportMediator;
import io.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionImportService;
import io.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionResolverService;
import io.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionXmlImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class DecisionDefinitionEngineImportMediatorFactory
    extends AbstractEngineImportMediatorFactory {

  private final DecisionDefinitionWriter decisionDefinitionWriter;
  private final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;

  public DecisionDefinitionEngineImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final DecisionDefinitionWriter decisionDefinitionWriter,
      final DecisionDefinitionXmlWriter decisionDefinitionXmlWriter,
      final DecisionDefinitionResolverService decisionDefinitionResolverService,
      final DatabaseClient databaseClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, databaseClient);
    this.decisionDefinitionWriter = decisionDefinitionWriter;
    this.decisionDefinitionXmlWriter = decisionDefinitionXmlWriter;
    this.decisionDefinitionResolverService = decisionDefinitionResolverService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return configurationService.isImportDmnDataEnabled()
        ? ImmutableList.of(
            createDecisionDefinitionEngineImportMediator(engineContext),
            createDecisionDefinitionXmlEngineImportMediator(engineContext))
        : Collections.emptyList();
  }

  public DecisionDefinitionEngineImportMediator createDecisionDefinitionEngineImportMediator(
      EngineContext engineContext) {
    return new DecisionDefinitionEngineImportMediator(
        importIndexHandlerRegistry.getDecisionDefinitionImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(DecisionDefinitionFetcher.class, engineContext),
        new DecisionDefinitionImportService(
            configurationService,
            engineContext,
            decisionDefinitionWriter,
            decisionDefinitionResolverService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }

  public DecisionDefinitionXmlEngineImportMediator createDecisionDefinitionXmlEngineImportMediator(
      EngineContext engineContext) {
    return new DecisionDefinitionXmlEngineImportMediator(
        importIndexHandlerRegistry.getDecisionDefinitionXmlImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(
            DecisionDefinitionXmlFetcher.class, engineContext, decisionDefinitionWriter),
        new DecisionDefinitionXmlImportService(
            configurationService,
            engineContext,
            decisionDefinitionXmlWriter,
            decisionDefinitionResolverService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }
}
