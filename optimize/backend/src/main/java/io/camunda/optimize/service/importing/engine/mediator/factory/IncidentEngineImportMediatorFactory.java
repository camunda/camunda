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
import io.camunda.optimize.service.db.writer.incident.CompletedIncidentWriter;
import io.camunda.optimize.service.db.writer.incident.OpenIncidentWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.CompletedIncidentFetcher;
import io.camunda.optimize.service.importing.engine.fetcher.instance.OpenIncidentFetcher;
import io.camunda.optimize.service.importing.engine.mediator.CompletedIncidentEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.OpenIncidentEngineImportMediator;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import io.camunda.optimize.service.importing.engine.service.incident.CompletedIncidentImportService;
import io.camunda.optimize.service.importing.engine.service.incident.OpenIncidentImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class IncidentEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {

  private final CompletedIncidentWriter completedIncidentWriter;
  private final OpenIncidentWriter openIncidentWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public IncidentEngineImportMediatorFactory(
      final CompletedIncidentWriter completedIncidentWriter,
      final OpenIncidentWriter openIncidentWriter,
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final DatabaseClient databaseClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, databaseClient);
    this.completedIncidentWriter = completedIncidentWriter;
    this.openIncidentWriter = openIncidentWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(
        createCompletedIncidentEngineImportMediator(engineContext),
        createOpenIncidentEngineImportMediator(engineContext));
  }

  private CompletedIncidentEngineImportMediator createCompletedIncidentEngineImportMediator(
      final EngineContext engineContext) {
    return new CompletedIncidentEngineImportMediator(
        importIndexHandlerRegistry.getCompletedIncidentImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(CompletedIncidentFetcher.class, engineContext),
        new CompletedIncidentImportService(
            configurationService,
            completedIncidentWriter,
            engineContext,
            processDefinitionResolverService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }

  private OpenIncidentEngineImportMediator createOpenIncidentEngineImportMediator(
      final EngineContext engineContext) {
    return new OpenIncidentEngineImportMediator(
        importIndexHandlerRegistry.getOpenIncidentImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(OpenIncidentFetcher.class, engineContext),
        new OpenIncidentImportService(
            configurationService,
            openIncidentWriter,
            engineContext,
            processDefinitionResolverService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }
}
