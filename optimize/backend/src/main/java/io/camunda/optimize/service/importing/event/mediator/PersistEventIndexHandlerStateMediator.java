/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.event.mediator;

import io.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ImportIndexWriter;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.AbstractStoreIndexesImportMediator;
import io.camunda.optimize.service.importing.engine.service.StoreIndexesEngineImportService;
import io.camunda.optimize.service.importing.event.handler.EventImportIndexHandlerRegistry;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PersistEventIndexHandlerStateMediator
    extends AbstractStoreIndexesImportMediator<StoreIndexesEngineImportService>
    implements ImportMediator {

  protected EventImportIndexHandlerRegistry importIndexHandlerRegistry;

  protected PersistEventIndexHandlerStateMediator(
      final ConfigurationService configurationService,
      final ImportIndexWriter importIndexWriter,
      final EventImportIndexHandlerRegistry importIndexHandlerRegistry,
      final DatabaseClient databaseClient) {
    super(
        new StoreIndexesEngineImportService(
            configurationService, importIndexWriter, databaseClient),
        configurationService);
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    try {
      final List<EngineImportIndexDto> importIndices =
          List.of(
              importIndexHandlerRegistry
                  .getExternalEventTraceImportIndexHandler()
                  .getIndexStateDto());
      importService.executeImport(importIndices, () -> importCompleted.complete(null));
    } catch (Exception e) {
      log.error("Could not execute import for storing event processing index handlers!", e);
    }
    return importCompleted;
  }
}
