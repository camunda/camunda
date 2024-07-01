/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.mediator.factory;

import io.camunda.optimize.plugin.VariableImportAdapterProvider;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.variable.ProcessVariableUpdateWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.instance.VariableUpdateInstanceFetcher;
import io.camunda.optimize.service.importing.engine.mediator.VariableUpdateEngineImportMediator;
import io.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import io.camunda.optimize.service.importing.engine.service.VariableUpdateInstanceImportService;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class VariableUpdateEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {

  private final CamundaEventImportServiceFactory camundaEventImportServiceFactory;
  private final ProcessVariableUpdateWriter variableWriter;
  private final VariableImportAdapterProvider variableImportAdapterProvider;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ObjectVariableService objectVariableService;

  public VariableUpdateEngineImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final CamundaEventImportServiceFactory camundaEventImportServiceFactory,
      final ProcessVariableUpdateWriter variableWriter,
      final VariableImportAdapterProvider variableImportAdapterProvider,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final ObjectVariableService objectVariableService,
      final DatabaseClient databaseClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, databaseClient);
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
        importIndexHandlerRegistry.getVariableUpdateInstanceImportIndexHandler(
            engineContext.getEngineAlias()),
        beanFactory.getBean(VariableUpdateInstanceFetcher.class, engineContext),
        new VariableUpdateInstanceImportService(
            configurationService,
            variableImportAdapterProvider,
            variableWriter,
            camundaEventImportServiceFactory.createCamundaEventService(engineContext),
            engineContext,
            processDefinitionResolverService,
            objectVariableService,
            databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }
}
