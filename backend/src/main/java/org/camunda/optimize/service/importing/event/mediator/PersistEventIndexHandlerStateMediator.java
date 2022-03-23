/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event.mediator;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
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

  protected PersistEventIndexHandlerStateMediator(final ConfigurationService configurationService,
                                                  final ImportIndexWriter importIndexWriter,
                                                  final EventImportIndexHandlerRegistry importIndexHandlerRegistry) {
    super(new StoreIndexesEngineImportService(configurationService, importIndexWriter), configurationService);
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    try {
      final List<EngineImportIndexDto> importIndices = importIndexHandlerRegistry.getAllHandlers()
        .stream()
        .map(EngineImportIndexHandler::getIndexStateDto)
        .filter(EngineImportIndexDto.class::isInstance)
        .map(EngineImportIndexDto.class::cast)
        .collect(toList());
      importService.executeImport(importIndices, () -> importCompleted.complete(null));
    } catch (Exception e) {
      log.error("Could not execute import for storing event processing index handlers!", e);
    }
    return importCompleted;
  }

}
