/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event.mediator;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.index.ImportIndexDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.ImportIndexHandler;
import org.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import org.camunda.optimize.service.importing.engine.service.StoreIndexesEngineImportService;
import org.camunda.optimize.service.importing.event.handler.EventImportIndexHandlerRegistry;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.stream.Collectors.toList;

@Component
@Slf4j
public class PersistEventIndexHandlerStateMediator implements ImportMediator {

  @Autowired
  private ImportIndexWriter importIndexWriter;
  @Autowired
  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  @Autowired
  protected ConfigurationService configurationService;
  @Autowired
  protected EventImportIndexHandlerRegistry importIndexHandlerRegistry;

  private StoreIndexesEngineImportService importService;

  private OffsetDateTime dateUntilJobCreationIsBlocked;

  @PostConstruct
  public void init() {
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    importService = new StoreIndexesEngineImportService(importIndexWriter, elasticsearchImportJobExecutor);
  }

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    dateUntilJobCreationIsBlocked = calculateDateUntilJobCreationIsBlocked();
    try {
      final List<ImportIndexDto> importIndices = importIndexHandlerRegistry.getAllHandlers()
        .stream()
        .map(ImportIndexHandler::getIndexStateDto)
        .filter(indexDto -> indexDto instanceof ImportIndexDto)
        .map(indexDto -> (ImportIndexDto) indexDto)
        .collect(toList());
      importService.executeImport(importIndices, () -> importCompleted.complete(null));
    } catch (Exception e) {
      log.error("Could not execute import for storing event processing index handlers!", e);
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

}
