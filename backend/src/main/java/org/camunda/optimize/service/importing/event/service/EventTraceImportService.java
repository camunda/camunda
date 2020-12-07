/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.service.EventTraceStateService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.importing.EventCountAndTracesImportJob;
import org.camunda.optimize.service.importing.engine.service.ImportService;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class EventTraceImportService implements ImportService<EventDto> {

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EventTraceStateService eventTraceStateService;

  @Override
  public void executeImport(final List<EventDto> pageOfEvents, final Runnable importCompleteCallback) {
    log.trace("Importing external event traces.");

    boolean newDataIsAvailable = !pageOfEvents.isEmpty();
    if (newDataIsAvailable) {
      elasticsearchImportJobExecutor.executeImportJob(createElasticsearchImportJob(
        pageOfEvents,
        importCompleteCallback
      ));
    } else {
      importCompleteCallback.run();
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private EventCountAndTracesImportJob createElasticsearchImportJob(final List<EventDto> events,
                                                                    final Runnable callback) {
    final EventCountAndTracesImportJob importJob = new EventCountAndTracesImportJob(
      eventTraceStateService, callback
    );
    importJob.setEntitiesToImport(events);
    return importJob;
  }

}
