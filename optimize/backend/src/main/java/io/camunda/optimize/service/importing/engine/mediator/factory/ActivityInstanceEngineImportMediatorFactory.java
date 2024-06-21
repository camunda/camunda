/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator.factory;

import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.activity.CompletedActivityInstanceWriter;
import io.camunda.optimize.service.db.writer.activity.RunningActivityInstanceWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.CompletedActivityInstanceFetcher;
import io.camunda.optimize.service.importing.engine.fetcher.instance.RunningActivityInstanceFetcher;
import io.camunda.optimize.service.importing.engine.mediator.CompletedActivityInstanceEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.RunningActivityInstanceEngineImportMediator;
import io.camunda.optimize.service.importing.engine.service.CompletedActivityInstanceImportService;
import io.camunda.optimize.service.importing.engine.service.RunningActivityInstanceImportService;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ActivityInstanceEngineImportMediatorFactory
    extends AbstractEngineImportMediatorFactory {

  private final CamundaEventImportServiceFactory camundaEventImportServiceFactory;
  private final CompletedActivityInstanceWriter completedActivityInstanceWriter;
  private final RunningActivityInstanceWriter runningActivityInstanceWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public ActivityInstanceEngineImportMediatorFactory(
      final CamundaEventImportServiceFactory camundaEventImportServiceFactory,
      final CompletedActivityInstanceWriter completedActivityInstanceWriter,
      final RunningActivityInstanceWriter runningActivityInstanceWriter,
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final DatabaseClient databaseClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, databaseClient);
    this.camundaEventImportServiceFactory = camundaEventImportServiceFactory;
    this.completedActivityInstanceWriter = completedActivityInstanceWriter;
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return List.of(
        createCompletedActivityInstanceEngineImportMediator(engineContext),
        createRunningActivityInstanceEngineImportMediator(engineContext));
  }

  private CompletedActivityInstanceEngineImportMediator
  createCompletedActivityInstanceEngineImportMediator(final EngineContext engineContext) {

    return new CompletedActivityInstanceEngineImportMediator(
        importIndexHandlerRegistry.getCompletedActivityInstanceImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(CompletedActivityInstanceFetcher.class, engineContext),
        new CompletedActivityInstanceImportService(
            completedActivityInstanceWriter,
            camundaEventImportServiceFactory.createCamundaEventService(engineContext),
            engineContext,
            configurationService,
            processDefinitionResolverService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }

  private RunningActivityInstanceEngineImportMediator
  createRunningActivityInstanceEngineImportMediator(final EngineContext engineContext) {
    return new RunningActivityInstanceEngineImportMediator(
        importIndexHandlerRegistry.getRunningActivityInstanceImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(RunningActivityInstanceFetcher.class, engineContext),
        new RunningActivityInstanceImportService(
            runningActivityInstanceWriter,
            camundaEventImportServiceFactory.createCamundaEventService(engineContext),
            engineContext,
            configurationService,
            processDefinitionResolverService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }
}
