/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.service.importing.AbstractImportScheduler;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.service.ImportObserver;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Slf4j
public class EngineImportScheduler extends AbstractImportScheduler<EngineDataSourceDto> {
  // Iterating through this synchronized list is only thread-safe when synchronizing on the list itself, as per docs
  private final List<ImportObserver> importObservers = Collections.synchronizedList(new LinkedList<>());

  public EngineImportScheduler(final List<ImportMediator> importMediators,
                               final EngineDataSourceDto dataImportSourceDto) {
    super(importMediators, dataImportSourceDto);
  }

  public void subscribe(ImportObserver importObserver) {
    importObservers.add(importObserver);
  }

  public void unsubscribe(ImportObserver importObserver) {
    importObservers.remove(importObserver);
  }

  @Override
  public Future<Void> runImportRound(final boolean forceImport) {
    List<ImportMediator> currentImportRound = importMediators
      .stream()
      .filter(mediator -> forceImport || mediator.canImport())
      .collect(Collectors.toList());
    if (nothingToBeImported(currentImportRound)) {
      this.isImporting = false;
      if (!hasActiveImportJobs()) {
        notifyThatImportIsIdle();
      }
      if (!forceImport) {
        doBackoff();
      }
      return CompletableFuture.completedFuture(null);
    } else {
      this.isImporting = true;
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
