/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.service.EventTraceStateService;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.importing.EventCountAndTracesImportJob;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;

@Slf4j
public class EventTraceImportService implements ImportService<EventDto> {

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EventTraceStateService eventTraceStateService;

  public EventTraceImportService(final ConfigurationService configurationService,
                                 final EventTraceStateService eventTraceStateService) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.eventTraceStateService = eventTraceStateService;
  }

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
