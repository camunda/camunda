/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.usertask.CompletedUserTaskInstanceWriter;
import org.camunda.optimize.service.es.writer.usertask.RunningUserTaskInstanceWriter;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedUserTaskInstanceFetcher;
import org.camunda.optimize.service.importing.engine.fetcher.instance.RunningUserTaskInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.CompletedUserTaskEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.RunningUserTaskInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.CompletedUserTaskInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.RunningUserTaskInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserTaskInstanceEngineImportMediatorFactory extends AbstractImportMediatorFactory {
  private final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter;
  private final CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter;

  public UserTaskInstanceEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                     final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                     final ConfigurationService configurationService,
                                                     final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter,
                                                     final CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.runningUserTaskInstanceWriter = runningUserTaskInstanceWriter;
    this.completedUserTaskInstanceWriter = completedUserTaskInstanceWriter;
  }

  @Override
  public List<EngineImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(
      createRunningUserTaskInstanceEngineImportMediator(engineContext),
      createCompletedUserTaskInstanceEngineImportMediator(engineContext)
    );
  }

  public RunningUserTaskInstanceEngineImportMediator createRunningUserTaskInstanceEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new RunningUserTaskInstanceEngineImportMediator(
      importIndexHandlerRegistry.getRunningUserTaskInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(RunningUserTaskInstanceFetcher.class, engineContext),
      new RunningUserTaskInstanceImportService(
        runningUserTaskInstanceWriter,
        elasticsearchImportJobExecutor,
        engineContext
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

  public CompletedUserTaskEngineImportMediator createCompletedUserTaskInstanceEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new CompletedUserTaskEngineImportMediator(
      importIndexHandlerRegistry.getCompletedUserTaskInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(CompletedUserTaskInstanceFetcher.class, engineContext),
      new CompletedUserTaskInstanceImportService(
        completedUserTaskInstanceWriter,
        elasticsearchImportJobExecutor,
        engineContext
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

}
