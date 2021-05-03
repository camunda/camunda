/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.incident.CompletedIncidentWriter;
import org.camunda.optimize.service.es.writer.incident.OpenIncidentWriter;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedIncidentFetcher;
import org.camunda.optimize.service.importing.engine.fetcher.instance.OpenIncidentFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.CompletedIncidentEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.OpenIncidentEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.incident.CompletedIncidentImportService;
import org.camunda.optimize.service.importing.engine.service.incident.OpenIncidentImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IncidentEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {

  private final CompletedIncidentWriter completedIncidentWriter;
  private final OpenIncidentWriter openIncidentWriter;

  public IncidentEngineImportMediatorFactory(final CompletedIncidentWriter completedIncidentWriter,
                                             final OpenIncidentWriter openIncidentWriter,
                                             final BeanFactory beanFactory,
                                             final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                             final ConfigurationService configurationService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.completedIncidentWriter = completedIncidentWriter;
    this.openIncidentWriter = openIncidentWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(
      createCompletedIncidentEngineImportMediator(engineContext),
      createOpenIncidentEngineImportMediator(engineContext)
    );
  }

  private CompletedIncidentEngineImportMediator createCompletedIncidentEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new CompletedIncidentEngineImportMediator(
      importIndexHandlerRegistry.getCompletedIncidentImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(CompletedIncidentFetcher.class, engineContext),
      new CompletedIncidentImportService(
        completedIncidentWriter,
        elasticsearchImportJobExecutor,
        engineContext
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

  private OpenIncidentEngineImportMediator createOpenIncidentEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new OpenIncidentEngineImportMediator(
      importIndexHandlerRegistry.getOpenIncidentImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(OpenIncidentFetcher.class, engineContext),
      new OpenIncidentImportService(
        openIncidentWriter,
        elasticsearchImportJobExecutor,
        engineContext
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
