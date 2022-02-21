/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.ingested.mediator;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import org.camunda.optimize.service.importing.ImportIndexHandler;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.AbstractStoreIndexesImportMediator;
import org.camunda.optimize.service.importing.engine.service.StoreIndexesEngineImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class StoreIngestedImportProgressMediator
  extends AbstractStoreIndexesImportMediator<StoreIndexesEngineImportService> implements ImportMediator {

  private final ImportIndexHandlerRegistry importIndexHandlerRegistry;

  public StoreIngestedImportProgressMediator(final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                             final StoreIndexesEngineImportService importService,
                                             final ConfigurationService configurationService) {
    super(importService, configurationService);
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    try {
      final List<EngineImportIndexDto> importIndexes = importIndexHandlerRegistry.getAllIngestedImportHandlers()
        .stream()
        .map(ImportIndexHandler::getIndexStateDto)
        .filter(Objects::nonNull)
        .map(EngineImportIndexDto.class::cast)
        .collect(Collectors.toList());

      importService.executeImport(importIndexes, () -> importCompleted.complete(null));
    } catch (Exception e) {
      log.error("Could not execute import for storing ingested import information!", e);
      importCompleted.complete(null);
    }
    return importCompleted;
  }

}
