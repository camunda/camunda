/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.ingested.mediator.factory;

import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import org.camunda.optimize.service.importing.ingested.fetcher.ExternalVariableUpdateInstanceFetcher;
import org.camunda.optimize.service.importing.ingested.mediator.ExternalVariableUpdateEngineImportMediator;
import org.camunda.optimize.service.importing.ingested.service.ExternalVariableUpdateImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExternalVariableUpdateImportMediatorFactory extends AbstractIngestedImportMediatorFactory {
  private final ProcessVariableUpdateWriter variableWriter;
  private final ObjectVariableService objectVariableService;

  public ExternalVariableUpdateImportMediatorFactory(final BeanFactory beanFactory,
                                                     final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                     final ConfigurationService configurationService,
                                                     final ProcessVariableUpdateWriter variableWriter,
                                                     final ObjectVariableService objectVariableService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.variableWriter = variableWriter;
    this.objectVariableService = objectVariableService;
  }

  @Override
  public List<ImportMediator> createMediators() {
    return List.of(createVariableUpdateEngineImportMediator());
  }

  public ExternalVariableUpdateEngineImportMediator createVariableUpdateEngineImportMediator() {
    return new ExternalVariableUpdateEngineImportMediator(
      importIndexHandlerRegistry.getExternalVariableUpdateImportIndexHandler(),
      beanFactory.getBean(ExternalVariableUpdateInstanceFetcher.class),
      new ExternalVariableUpdateImportService(configurationService, variableWriter, objectVariableService),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
