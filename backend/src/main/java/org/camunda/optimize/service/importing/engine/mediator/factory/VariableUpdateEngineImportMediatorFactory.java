/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.plugin.VariableImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.VariableUpdateInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.VariableUpdateEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.VariableUpdateInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VariableUpdateEngineImportMediatorFactory extends AbstractImportMediatorFactory {
  private final CamundaEventImportServiceFactory camundaEventImportServiceFactory;
  private final ProcessVariableUpdateWriter variableWriter;
  private final VariableImportAdapterProvider variableImportAdapterProvider;

  public VariableUpdateEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                   final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                   final ConfigurationService configurationService,
                                                   final CamundaEventImportServiceFactory camundaEventImportServiceFactory,
                                                   final ProcessVariableUpdateWriter variableWriter,
                                                   final VariableImportAdapterProvider variableImportAdapterProvider) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.camundaEventImportServiceFactory = camundaEventImportServiceFactory;
    this.variableWriter = variableWriter;
    this.variableImportAdapterProvider = variableImportAdapterProvider;
  }

  @Override
  public List<EngineImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(createVariableUpdateEngineImportMediator(engineContext));
  }

  public VariableUpdateEngineImportMediator createVariableUpdateEngineImportMediator(
    final EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new VariableUpdateEngineImportMediator(
      importIndexHandlerRegistry.getRunningVariableInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(VariableUpdateInstanceFetcher.class, engineContext),
      new VariableUpdateInstanceImportService(
        variableWriter,
        camundaEventImportServiceFactory.createCamundaEventService(engineContext),
        variableImportAdapterProvider,
        elasticsearchImportJobExecutor,
        engineContext
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
