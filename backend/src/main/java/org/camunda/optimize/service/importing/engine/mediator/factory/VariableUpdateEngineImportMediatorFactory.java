/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import org.camunda.optimize.plugin.VariableImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.VariableUpdateInstanceFetcher;
import org.camunda.optimize.service.importing.engine.mediator.VariableUpdateEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import org.camunda.optimize.service.importing.engine.service.VariableUpdateInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VariableUpdateEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {
  private final CamundaEventImportServiceFactory camundaEventImportServiceFactory;
  private final ProcessVariableUpdateWriter variableWriter;
  private final VariableImportAdapterProvider variableImportAdapterProvider;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ObjectVariableService objectVariableService;

  public VariableUpdateEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                   final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                   final ConfigurationService configurationService,
                                                   final CamundaEventImportServiceFactory camundaEventImportServiceFactory,
                                                   final ProcessVariableUpdateWriter variableWriter,
                                                   final VariableImportAdapterProvider variableImportAdapterProvider,
                                                   final ProcessDefinitionResolverService processDefinitionResolverService,
                                                   final ObjectVariableService objectVariableService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.camundaEventImportServiceFactory = camundaEventImportServiceFactory;
    this.variableWriter = variableWriter;
    this.variableImportAdapterProvider = variableImportAdapterProvider;
    this.processDefinitionResolverService = processDefinitionResolverService;
    this.objectVariableService = objectVariableService;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return List.of(createVariableUpdateEngineImportMediator(engineContext));
  }

  public VariableUpdateEngineImportMediator createVariableUpdateEngineImportMediator(
    final EngineContext engineContext) {
    return new VariableUpdateEngineImportMediator(
      importIndexHandlerRegistry.getVariableUpdateInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(VariableUpdateInstanceFetcher.class, engineContext),
      new VariableUpdateInstanceImportService(
        configurationService,
        variableImportAdapterProvider,
        variableWriter,
        camundaEventImportServiceFactory.createCamundaEventService(engineContext),
        engineContext,
        processDefinitionResolverService,
        objectVariableService
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
