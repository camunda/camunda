/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.plugin.BusinessKeyImportAdapterProvider;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import io.camunda.optimize.service.db.writer.CompletedProcessInstanceWriter;
import io.camunda.optimize.service.db.writer.RunningProcessInstanceWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.CompletedProcessInstanceFetcher;
import io.camunda.optimize.service.importing.engine.fetcher.instance.RunningProcessInstanceFetcher;
import io.camunda.optimize.service.importing.engine.mediator.CompletedProcessInstanceEngineImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.RunningProcessInstanceEngineImportMediator;
import io.camunda.optimize.service.importing.engine.service.CompletedProcessInstanceImportService;
import io.camunda.optimize.service.importing.engine.service.RunningProcessInstanceImportService;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceEngineImportMediatorFactory
    extends AbstractEngineImportMediatorFactory {

  private final CamundaEventImportServiceFactory camundaEventImportServiceFactory;
  private final CompletedProcessInstanceWriter completedProcessInstanceWriter;
  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private final BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ProcessInstanceRepository processInstanceRepository;

  public ProcessInstanceEngineImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final CamundaEventImportServiceFactory camundaEventImportServiceFactory,
      final CompletedProcessInstanceWriter completedProcessInstanceWriter,
      final RunningProcessInstanceWriter runningProcessInstanceWriter,
      final BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final DatabaseClient databaseClient,
      final ProcessInstanceRepository processInstanceRepository) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, databaseClient);
    this.camundaEventImportServiceFactory = camundaEventImportServiceFactory;
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.businessKeyImportAdapterProvider = businessKeyImportAdapterProvider;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.processInstanceRepository = processInstanceRepository;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(
        createCompletedProcessInstanceEngineImportMediator(engineContext),
        createRunningProcessInstanceEngineImportMediator(engineContext));
  }

  public CompletedProcessInstanceEngineImportMediator
      createCompletedProcessInstanceEngineImportMediator(EngineContext engineContext) {
    return new CompletedProcessInstanceEngineImportMediator(
        importIndexHandlerRegistry.getCompletedProcessInstanceImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(CompletedProcessInstanceFetcher.class, engineContext),
        new CompletedProcessInstanceImportService(
            configurationService,
            engineContext,
            businessKeyImportAdapterProvider,
            completedProcessInstanceWriter,
            camundaEventImportServiceFactory.createCamundaEventService(engineContext),
            processDefinitionResolverService,
            databaseClient,
            processInstanceRepository),
        configurationService,
        new BackoffCalculator(configurationService));
  }

  public RunningProcessInstanceEngineImportMediator
      createRunningProcessInstanceEngineImportMediator(EngineContext engineContext) {
    return new RunningProcessInstanceEngineImportMediator(
        importIndexHandlerRegistry.getRunningProcessInstanceImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(RunningProcessInstanceFetcher.class, engineContext),
        new RunningProcessInstanceImportService(
            configurationService,
            engineContext,
            businessKeyImportAdapterProvider,
            runningProcessInstanceWriter,
            camundaEventImportServiceFactory.createCamundaEventService(engineContext),
            processDefinitionResolverService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }
}
