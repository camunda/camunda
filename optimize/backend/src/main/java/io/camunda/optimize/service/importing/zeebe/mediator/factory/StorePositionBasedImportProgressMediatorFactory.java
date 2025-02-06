/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.PositionBasedImportIndexWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.StorePositionBasedIndexImportService;
import io.camunda.optimize.service.importing.zeebe.mediator.StorePositionBasedImportProgressMediator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class StorePositionBasedImportProgressMediatorFactory
    extends AbstractZeebeImportMediatorFactory {

  private final PositionBasedImportIndexWriter importIndexWriter;

  public StorePositionBasedImportProgressMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final PositionBasedImportIndexWriter importIndexWriter,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper,
      final DatabaseClient databaseClient) {
    super(
        beanFactory,
        importIndexHandlerRegistry,
        configurationService,
        objectMapper,
        databaseClient);
    this.importIndexWriter = importIndexWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto dataSourceDto) {
    return List.of(
        new StorePositionBasedImportProgressMediator(
            importIndexHandlerRegistry,
            new StorePositionBasedIndexImportService(
                configurationService, importIndexWriter, databaseClient),
            configurationService,
            dataSourceDto));
  }
}
