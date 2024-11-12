/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.ingested.mediator.factory;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ImportIndexWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.StoreTimestampBasedImportIndexImportService;
import io.camunda.optimize.service.importing.ingested.mediator.StoreIngestedImportProgressMediator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class StoreIngestedImportProgressMediatorFactory
    extends AbstractIngestedImportMediatorFactory {

  private final ImportIndexWriter importIndexWriter;
  private final DatabaseClient databaseClient;

  public StoreIngestedImportProgressMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final ImportIndexWriter importIndexWriter,
      final DatabaseClient databaseClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.importIndexWriter = importIndexWriter;
    this.databaseClient = databaseClient;
  }

  @Override
  public List<ImportMediator> createMediators() {
    return List.of(
        new StoreIngestedImportProgressMediator(
            importIndexHandlerRegistry,
            new StoreTimestampBasedImportIndexImportService(
                configurationService, importIndexWriter, databaseClient),
            configurationService));
  }
}
