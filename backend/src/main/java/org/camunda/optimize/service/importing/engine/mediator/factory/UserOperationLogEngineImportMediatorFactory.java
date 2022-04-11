/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.UserOperationLogFetcher;
import org.camunda.optimize.service.importing.engine.mediator.UserOperationLogEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.ProcessInstanceResolverService;
import org.camunda.optimize.service.importing.engine.service.UserOperationLogImportService;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserOperationLogEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {
  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ProcessInstanceResolverService processInstanceResolverService;

  public UserOperationLogEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                     final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                     final ConfigurationService configurationService,
                                                     final RunningProcessInstanceWriter runningProcessInstanceWriter,
                                                     final ProcessDefinitionResolverService processDefinitionResolverService,
                                                     final ProcessInstanceResolverService processInstanceResolverService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.processInstanceResolverService = processInstanceResolverService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(
      createUserOperationLogEngineImportMediator(engineContext)
    );
  }

  public UserOperationLogEngineImportMediator createUserOperationLogEngineImportMediator(
    final EngineContext engineContext) {
    return new UserOperationLogEngineImportMediator(
      importIndexHandlerRegistry.getUserOperationsLogImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(UserOperationLogFetcher.class, engineContext),
      new UserOperationLogImportService(
        configurationService,
        runningProcessInstanceWriter,
        importIndexHandlerRegistry.getRunningProcessInstanceImportIndexHandler(engineContext.getEngineAlias()),
        processDefinitionResolverService,
        processInstanceResolverService
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
