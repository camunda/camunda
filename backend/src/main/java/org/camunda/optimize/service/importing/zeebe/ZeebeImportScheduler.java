/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.zeebe;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ZeebeDataSourceDto;
import org.camunda.optimize.service.importing.AbstractImportScheduler;
import org.camunda.optimize.service.importing.ImportMediator;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Slf4j
public class ZeebeImportScheduler extends AbstractImportScheduler<ZeebeDataSourceDto> {

  public ZeebeImportScheduler(final List<ImportMediator> importMediators,
                              final ZeebeDataSourceDto dataImportSourceDto) {
    super(importMediators, dataImportSourceDto);
  }

  @Override
  public Future<Void> runImportRound(final boolean forceImport) {
    List<ImportMediator> currentImportRound = importMediators
      .stream()
      .filter(mediator -> forceImport || mediator.canImport())
      .collect(Collectors.toList());
    if (nothingToBeImported(currentImportRound)) {
      this.isImporting = false;
      if (!forceImport) {
        doBackoff();
      }
      return CompletableFuture.completedFuture(null);
    } else {
      this.isImporting = true;
      return executeImportRound(currentImportRound);
    }
  }

}
