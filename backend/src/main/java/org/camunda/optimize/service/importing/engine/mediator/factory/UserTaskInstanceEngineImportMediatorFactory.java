/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.usertask.CompletedUserTaskInstanceWriter;
import org.camunda.optimize.service.es.writer.usertask.RunningUserTaskInstanceWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedUserTaskInstanceFetcher;
import org.camunda.optimize.service.importing.engine.fetcher.instance.RunningUserTaskInstanceFetcher;
import org.camunda.optimize.service.importing.engine.mediator.CompletedUserTaskEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.RunningUserTaskInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.CompletedUserTaskInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.RunningUserTaskInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserTaskInstanceEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {
  private final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter;
  private final CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;

  public UserTaskInstanceEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                     final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                     final ConfigurationService configurationService,
                                                     final RunningUserTaskInstanceWriter runningUserTaskInstanceWriter,
                                                     final CompletedUserTaskInstanceWriter completedUserTaskInstanceWriter,
                                                     final ProcessDefinitionResolverService processDefinitionResolverService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.runningUserTaskInstanceWriter = runningUserTaskInstanceWriter;
    this.completedUserTaskInstanceWriter = completedUserTaskInstanceWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return List.of(
      createRunningUserTaskInstanceEngineImportMediator(engineContext),
      createCompletedUserTaskInstanceEngineImportMediator(engineContext)
    );
  }

  public RunningUserTaskInstanceEngineImportMediator createRunningUserTaskInstanceEngineImportMediator(
    EngineContext engineContext) {
    return new RunningUserTaskInstanceEngineImportMediator(
      importIndexHandlerRegistry.getRunningUserTaskInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(RunningUserTaskInstanceFetcher.class, engineContext),
      new RunningUserTaskInstanceImportService(
        configurationService, runningUserTaskInstanceWriter, engineContext, processDefinitionResolverService
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

  public CompletedUserTaskEngineImportMediator createCompletedUserTaskInstanceEngineImportMediator(
    EngineContext engineContext) {
    return new CompletedUserTaskEngineImportMediator(
      importIndexHandlerRegistry.getCompletedUserTaskInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(CompletedUserTaskInstanceFetcher.class, engineContext),
      new CompletedUserTaskInstanceImportService(
        configurationService, completedUserTaskInstanceWriter, engineContext, processDefinitionResolverService
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

}
