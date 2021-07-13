/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event.mediator;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.importing.EngineImportIndexHandler;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.AbstractStoreIndexesImportMediator;
import org.camunda.optimize.service.importing.engine.service.StoreIndexesEngineImportService;
import org.camunda.optimize.service.importing.event.handler.EventImportIndexHandlerRegistry;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.stream.Collectors.toList;

@Component
@Slf4j
public class PersistEventIndexHandlerStateMediator
  extends AbstractStoreIndexesImportMediator<StoreIndexesEngineImportService> implements ImportMediator {

  protected EventImportIndexHandlerRegistry importIndexHandlerRegistry;

  protected PersistEventIndexHandlerStateMediator(final ImportIndexWriter importIndexWriter,
                                                  final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                                  final EventImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                  final ConfigurationService configurationService) {
    super(new StoreIndexesEngineImportService(importIndexWriter, elasticsearchImportJobExecutor), configurationService);
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    try {
      final List<ImportIndexDto> importIndices = importIndexHandlerRegistry.getAllHandlers()
        .stream()
        .map(EngineImportIndexHandler::getIndexStateDto)
        .filter(ImportIndexDto.class::isInstance)
        .map(ImportIndexDto.class::cast)
        .collect(toList());
      importService.executeImport(importIndices, () -> importCompleted.complete(null));
    } catch (Exception e) {
      log.error("Could not execute import for storing event processing index handlers!", e);
    }
    return importCompleted;
  }

}
