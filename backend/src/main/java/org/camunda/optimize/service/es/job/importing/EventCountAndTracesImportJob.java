/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.service.EventTraceStateService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;

import java.util.List;

public class EventCountAndTracesImportJob extends ElasticsearchImportJob<EventDto> {
  private final EventTraceStateService eventTraceStateService;

  public EventCountAndTracesImportJob(final EventTraceStateService eventTraceStateService,
                                      final Runnable callback) {
    super(callback);
    this.eventTraceStateService = eventTraceStateService;
  }

  @Override
  protected void persistEntities(final List<EventDto> eventDtos) {
    eventTraceStateService.updateTracesAndCountsForEvents(eventDtos);
  }
}
