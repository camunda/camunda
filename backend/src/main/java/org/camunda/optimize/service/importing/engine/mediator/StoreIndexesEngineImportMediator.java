/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.index.ImportIndexDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.ImportIndexHandler;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.service.StoreIndexesEngineImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class StoreIndexesEngineImportMediator implements ImportMediator {
  protected EngineContext engineContext;
  private ConfigurationService configurationService;
  private EngineImportIndexHandlerRegistry importIndexHandlerRegistry;
  private StoreIndexesEngineImportService importService;
  private OffsetDateTime dateUntilJobCreationIsBlocked;


  public StoreIndexesEngineImportMediator(final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                          final StoreIndexesEngineImportService importService,
                                          final EngineContext engineContext,
                                          final ConfigurationService configurationService) {
    this.configurationService = configurationService;
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
    this.dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    this.importService = importService;
    this.engineContext = engineContext;
    this.configurationService = configurationService;
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    try {
      final List<ImportIndexDto> importIndexes = Stream
        .concat(
          createStreamForHandlers(importIndexHandlerRegistry.getAllEntitiesBasedHandlers(engineContext.getEngineAlias())),
          createStreamForHandlers(importIndexHandlerRegistry.getTimestampEngineBasedHandlers(engineContext.getEngineAlias()))
        )
        .map(ImportIndexHandler::getIndexStateDto)
        .filter(indexStateDto -> indexStateDto instanceof ImportIndexDto)
        .map(indexStateDto -> (ImportIndexDto) indexStateDto)
        .collect(Collectors.toList());

      importService.executeImport(importIndexes, () -> importCompleted.complete(null));
    } catch (Exception e) {
      log.error("Could not execute import for storing index information!", e);
      importCompleted.complete(null);
    }
    return importCompleted;
  }

  @Override
  public long getBackoffTimeInMs() {
    long backoffTime = OffsetDateTime.now().until(dateUntilJobCreationIsBlocked, ChronoUnit.MILLIS);
    backoffTime = Math.max(0, backoffTime);
    return backoffTime;
  }

  @Override
  public void resetBackoff() {
    this.dateUntilJobCreationIsBlocked = OffsetDateTime.MIN;
  }

  @Override
  public boolean canImport() {
    return OffsetDateTime.now().isAfter(dateUntilJobCreationIsBlocked);
  }

  @Override
  public boolean hasPendingImportJobs() {
    return importService.hasPendingImportJobs();
  }

  @Override
  public void shutdown() {
    importService.shutdown();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.IMPORT_META_DATA;
  }

  private OffsetDateTime calculateDateUntilJobCreationIsBlocked() {
    return OffsetDateTime.now().plusSeconds(configurationService.getImportIndexAutoStorageIntervalInSec());
  }

  private <T extends ImportIndexHandler> Stream<T> createStreamForHandlers(List<T> handlers) {
    return Optional.ofNullable(handlers)
      .map(Collection::stream)
      .orElseGet(Stream::empty);
  }

}
