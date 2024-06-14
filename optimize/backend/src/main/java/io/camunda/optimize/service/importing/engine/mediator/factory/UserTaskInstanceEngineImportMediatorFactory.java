/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.engine.mediator.factory;

import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.usertask.CompletedUserTaskInstanceWriter;
import io.camunda.optimize.service.db.writer.usertask.RunningUserTaskInstanceWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.CompletedUserTaskInstanceFetcher;
import io.camunda.optimize.service.importing.engine.fetcher.instance.RunningUserTaskInstanceFetcher;
import io.camunda.optimize.service.importing.engine.mediator.CompletedUserTaskEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.RunningUserTaskInstanceEngineImportMediator;
import io.camunda.optimize.service.importing.engine.service.CompletedUserTaskInstanceImportService;
import io.camunda.optimize.service.importing.engine.service.RunningUserTaskInstanceImportService;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class UserTaskInstanceEngineImportMediatorFactory
    extends AbstractEngineImportMediatorFactory {
  private final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter;
  private final CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public UserTaskInstanceEngineImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter,
      final CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final DatabaseClient databaseClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, databaseClient);
    this.runningUserTaskInstanceWriter = runningUserTaskInstanceWriter;
    this.completedUserTaskInstanceWriter = completedUserTaskInstanceWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return List.of(
        createRunningUserTaskInstanceEngineImportMediator(engineContext),
        createCompletedUserTaskInstanceEngineImportMediator(engineContext));
  }

  public RunningUserTaskInstanceEngineImportMediator
      createRunningUserTaskInstanceEngineImportMediator(EngineContext engineContext) {
    return new RunningUserTaskInstanceEngineImportMediator(
        importIndexHandlerRegistry.getRunningUserTaskInstanceImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(RunningUserTaskInstanceFetcher.class, engineContext),
        new RunningUserTaskInstanceImportService(
            configurationService,
            runningUserTaskInstanceWriter,
            engineContext,
            processDefinitionResolverService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }

  public CompletedUserTaskEngineImportMediator createCompletedUserTaskInstanceEngineImportMediator(
      EngineContext engineContext) {
    return new CompletedUserTaskEngineImportMediator(
        importIndexHandlerRegistry.getCompletedUserTaskInstanceImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(CompletedUserTaskInstanceFetcher.class, engineContext),
        new CompletedUserTaskInstanceImportService(
            configurationService,
            completedUserTaskInstanceWriter,
            engineContext,
            processDefinitionResolverService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }
}
