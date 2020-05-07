/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.CompletedActivityInstanceWriter;
import org.camunda.optimize.service.es.writer.RunningActivityInstanceWriter;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedActivityInstanceFetcher;
import org.camunda.optimize.service.importing.engine.fetcher.instance.RunningActivityInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.CompletedActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.RunningActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.CompletedActivityInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.RunningActivityInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ActivityInstanceEngineImportMediatorFactory extends AbstractImportMediatorFactory {
  private final CamundaEventImportService camundaEventService;
  private final CompletedActivityInstanceWriter completedActivityInstanceWriter;
  private final RunningActivityInstanceWriter runningActivityInstanceWriter;

  public ActivityInstanceEngineImportMediatorFactory(final CamundaEventImportService camundaEventService,
                                                     final CompletedActivityInstanceWriter completedActivityInstanceWriter,
                                                     final RunningActivityInstanceWriter runningActivityInstanceWriter,
                                                     final BeanFactory beanFactory,
                                                     final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                     final ConfigurationService configurationService,
                                                     final BackoffCalculator idleBackoffCalculator) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, idleBackoffCalculator);
    this.camundaEventService = camundaEventService;
    this.completedActivityInstanceWriter = completedActivityInstanceWriter;
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
  }

  public CompletedActivityInstanceEngineImportMediator createCompletedActivityInstanceEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new CompletedActivityInstanceEngineImportMediator(
      importIndexHandlerRegistry.getCompletedActivityInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(CompletedActivityInstanceFetcher.class, engineContext),
      new CompletedActivityInstanceImportService(
        completedActivityInstanceWriter,
        camundaEventService,
        elasticsearchImportJobExecutor,
        engineContext
      ),
      configurationService,
      elasticsearchImportJobExecutor,
      idleBackoffCalculator
    );
  }

  public RunningActivityInstanceEngineImportMediator createRunningActivityInstanceEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new RunningActivityInstanceEngineImportMediator(
      importIndexHandlerRegistry.getRunningActivityInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(RunningActivityInstanceFetcher.class, engineContext),
      new RunningActivityInstanceImportService(
        runningActivityInstanceWriter,
        camundaEventService,
        elasticsearchImportJobExecutor,
        engineContext
      ),
      configurationService,
      elasticsearchImportJobExecutor,
      idleBackoffCalculator
    );
  }
}
