/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.activity.CompletedActivityInstanceWriter;
import org.camunda.optimize.service.es.writer.activity.RunningActivityInstanceWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedActivityInstanceFetcher;
import org.camunda.optimize.service.importing.engine.fetcher.instance.RunningActivityInstanceFetcher;
import org.camunda.optimize.service.importing.engine.mediator.CompletedActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.RunningActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.CompletedActivityInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.RunningActivityInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ActivityInstanceEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {
  private final CamundaEventImportServiceFactory camundaEventImportServiceFactory;
  private final CompletedActivityInstanceWriter completedActivityInstanceWriter;
  private final RunningActivityInstanceWriter runningActivityInstanceWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public ActivityInstanceEngineImportMediatorFactory(final CamundaEventImportServiceFactory camundaEventImportServiceFactory,
                                                     final CompletedActivityInstanceWriter completedActivityInstanceWriter,
                                                     final RunningActivityInstanceWriter runningActivityInstanceWriter,
                                                     final BeanFactory beanFactory,
                                                     final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                     final ConfigurationService configurationService,
                                                     final ProcessDefinitionResolverService processDefinitionResolverService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.camundaEventImportServiceFactory = camundaEventImportServiceFactory;
    this.completedActivityInstanceWriter = completedActivityInstanceWriter;
    this.runningActivityInstanceWriter = runningActivityInstanceWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return List.of(
      createCompletedActivityInstanceEngineImportMediator(engineContext),
      createRunningActivityInstanceEngineImportMediator(engineContext)
    );
  }

  private CompletedActivityInstanceEngineImportMediator createCompletedActivityInstanceEngineImportMediator(
    EngineContext engineContext) {

    return new CompletedActivityInstanceEngineImportMediator(
      importIndexHandlerRegistry.getCompletedActivityInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(CompletedActivityInstanceFetcher.class, engineContext),
      new CompletedActivityInstanceImportService(
        completedActivityInstanceWriter,
        camundaEventImportServiceFactory.createCamundaEventService(engineContext),
        engineContext,
        configurationService,
        processDefinitionResolverService
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

  private RunningActivityInstanceEngineImportMediator createRunningActivityInstanceEngineImportMediator(
    EngineContext engineContext) {
    return new RunningActivityInstanceEngineImportMediator(
      importIndexHandlerRegistry.getRunningActivityInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(RunningActivityInstanceFetcher.class, engineContext),
      new RunningActivityInstanceImportService(
        runningActivityInstanceWriter,
        camundaEventImportServiceFactory.createCamundaEventService(engineContext),
        engineContext,
        configurationService,
        processDefinitionResolverService
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
