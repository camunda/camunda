/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.ingested.mediator.factory;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.variable.ProcessVariableUpdateWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import io.camunda.optimize.service.importing.ingested.fetcher.ExternalVariableUpdateInstanceFetcher;
import io.camunda.optimize.service.importing.ingested.mediator.ExternalVariableUpdateEngineImportMediator;
import io.camunda.optimize.service.importing.ingested.service.ExternalVariableUpdateImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ExternalVariableUpdateImportMediatorFactory
    extends AbstractIngestedImportMediatorFactory {

  private final ProcessVariableUpdateWriter variableWriter;
  private final ObjectVariableService objectVariableService;
  private final DatabaseClient databaseClient;

  public ExternalVariableUpdateImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final ProcessVariableUpdateWriter variableWriter,
      final ObjectVariableService objectVariableService,
      final DatabaseClient databaseClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.variableWriter = variableWriter;
    this.objectVariableService = objectVariableService;
    this.databaseClient = databaseClient;
  }

  @Override
  public List<ImportMediator> createMediators() {
    return List.of(createVariableUpdateEngineImportMediator());
  }

  public ExternalVariableUpdateEngineImportMediator createVariableUpdateEngineImportMediator() {
    return new ExternalVariableUpdateEngineImportMediator(
        importIndexHandlerRegistry.getExternalVariableUpdateImportIndexHandler(),
        beanFactory.getBean(ExternalVariableUpdateInstanceFetcher.class),
        new ExternalVariableUpdateImportService(
            configurationService, variableWriter, objectVariableService, databaseClient),
        configurationService,
        new BackoffCalculator(configurationService));
  }
}
