/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine;

import io.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import io.camunda.optimize.service.importing.AbstractImportScheduler;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.ImportObserver;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EngineImportScheduler extends AbstractImportScheduler<EngineDataSourceDto> {

  // Iterating through this synchronized list is only thread-safe when synchronizing on the list
  // itself, as per docs
  private final List<ImportObserver> importObservers =
      Collections.synchronizedList(new LinkedList<>());

  public EngineImportScheduler(
      final List<ImportMediator> importMediators, final EngineDataSourceDto dataImportSourceDto) {
    super(importMediators, dataImportSourceDto);
  }

  public void subscribe(final ImportObserver importObserver) {
    importObservers.add(importObserver);
  }

  public void unsubscribe(final ImportObserver importObserver) {
    importObservers.remove(importObserver);
  }

  @Override
  public Future<Void> runImportRound(final boolean forceImport) {
    final List<ImportMediator> currentImportRound =
        importMediators.stream()
            .filter(mediator -> forceImport || mediator.canImport())
            .collect(Collectors.toList());
    if (nothingToBeImported(currentImportRound)) {
      isImporting = false;
      if (!hasActiveImportJobs()) {
        notifyThatImportIsIdle();
      }
      if (!forceImport) {
        doBackoff();
      }
      return CompletableFuture.completedFuture(null);
    } else {
      isImporting = true;
      notifyThatImportIsInProgress();
      return executeImportRound(currentImportRound);
    }
  }

  public String getEngineAlias() {
    return dataImportSourceDto.getName();
  }

  private void notifyThatImportIsInProgress() {
    synchronized (importObservers) {
      for (final ImportObserver importObserver : importObservers) {
        importObserver.importInProgress(dataImportSourceDto.getName());
      }
    }
  }

  private void notifyThatImportIsIdle() {
    synchronized (importObservers) {
      for (final ImportObserver importObserver : importObservers) {
        importObserver.importIsIdle(dataImportSourceDto.getName());
      }
    }
  }
}
