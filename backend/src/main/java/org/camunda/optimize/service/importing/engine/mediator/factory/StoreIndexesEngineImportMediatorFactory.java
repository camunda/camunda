/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.StoreIndexesEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.StoreIndexesEngineImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StoreIndexesEngineImportMediatorFactory extends AbstractEngineImportMediatorFactory {
  private final ImportIndexWriter importIndexWriter;

  public StoreIndexesEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                 final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                 final ConfigurationService configurationService,
                                                 final ImportIndexWriter importIndexWriter) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.importIndexWriter = importIndexWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final EngineContext engineContext) {
    return ImmutableList.of(createStoreIndexImportMediator(engineContext));
  }

  public StoreIndexesEngineImportMediator createStoreIndexImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new StoreIndexesEngineImportMediator(
      importIndexHandlerRegistry,
      new StoreIndexesEngineImportService(importIndexWriter, elasticsearchImportJobExecutor),
      engineContext,
      configurationService
    );
  }

}
