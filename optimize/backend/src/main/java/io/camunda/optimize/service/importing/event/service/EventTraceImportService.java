/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.event.service;

import io.camunda.optimize.dto.optimize.query.event.process.EventDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.events.EventTraceStateService;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.EventCountAndTracesImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventTraceImportService implements ImportService<EventDto> {

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final EventTraceStateService eventTraceStateService;
  private final DatabaseClient databaseClient;

  public EventTraceImportService(
      final ConfigurationService configurationService,
      final EventTraceStateService eventTraceStateService,
      final DatabaseClient databaseClient) {
    this.databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.eventTraceStateService = eventTraceStateService;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<EventDto> pageOfEvents, final Runnable importCompleteCallback) {
    log.trace("Importing external event traces.");

    boolean newDataIsAvailable = !pageOfEvents.isEmpty();
    if (newDataIsAvailable) {
      databaseImportJobExecutor.executeImportJob(
          createDatabaseImportJob(pageOfEvents, importCompleteCallback));
    } else {
      importCompleteCallback.run();
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private EventCountAndTracesImportJob createDatabaseImportJob(
      final List<EventDto> events, final Runnable callback) {
    final EventCountAndTracesImportJob importJob =
        new EventCountAndTracesImportJob(eventTraceStateService, callback, databaseClient);
    importJob.setEntitiesToImport(events);
    return importJob;
  }
}
