/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.ingested.mediator.factory;

import java.util.List;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.ImportIndexWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.service.StoreIndexesEngineImportService;
import org.camunda.optimize.service.importing.ingested.mediator.StoreIngestedImportProgressMediator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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
            new StoreIndexesEngineImportService(
                configurationService, importIndexWriter, databaseClient),
            configurationService));
  }
}
