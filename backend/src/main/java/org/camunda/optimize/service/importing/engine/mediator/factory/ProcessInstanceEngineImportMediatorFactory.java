/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.plugin.BusinessKeyImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedProcessInstanceFetcher;
import org.camunda.optimize.service.importing.engine.fetcher.instance.RunningProcessInstanceFetcher;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.CompletedProcessInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.RunningProcessInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.CompletedProcessInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.RunningProcessInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessInstanceEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {
  private final CamundaEventImportServiceFactory camundaEventImportServiceFactory;
  private final CompletedProcessInstanceWriter completedProcessInstanceWriter;
  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private final BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider;

  public ProcessInstanceEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                    final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                    final ConfigurationService configurationService,
                                                    final CamundaEventImportServiceFactory camundaEventImportServiceFactory,
                                                    final CompletedProcessInstanceWriter completedProcessInstanceWriter,
                                                    final RunningProcessInstanceWriter runningProcessInstanceWriter,
                                                    final BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.camundaEventImportServiceFactory = camundaEventImportServiceFactory;
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.businessKeyImportAdapterProvider = businessKeyImportAdapterProvider;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(
      createCompletedProcessInstanceEngineImportMediator(engineContext),
      createRunningProcessInstanceEngineImportMediator(engineContext)
    );
  }

  public CompletedProcessInstanceEngineImportMediator createCompletedProcessInstanceEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new CompletedProcessInstanceEngineImportMediator(
      importIndexHandlerRegistry.getCompletedProcessInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(CompletedProcessInstanceFetcher.class, engineContext),
      new CompletedProcessInstanceImportService(
        elasticsearchImportJobExecutor,
        engineContext,
        businessKeyImportAdapterProvider,
        completedProcessInstanceWriter,
        camundaEventImportServiceFactory.createCamundaEventService(engineContext)
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

  public RunningProcessInstanceEngineImportMediator createRunningProcessInstanceEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new RunningProcessInstanceEngineImportMediator(
      importIndexHandlerRegistry.getRunningProcessInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(RunningProcessInstanceFetcher.class, engineContext),
      new RunningProcessInstanceImportService(
        elasticsearchImportJobExecutor,
        engineContext,
        businessKeyImportAdapterProvider,
        runningProcessInstanceWriter,
        camundaEventImportServiceFactory.createCamundaEventService(engineContext)
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

}
