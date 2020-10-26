/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.query.event.process.EventResponseDto;
import org.camunda.optimize.service.EventTraceStateService;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;

import java.util.List;

public class EventCountAndTracesImportJob extends ElasticsearchImportJob<EventResponseDto> {
  private final EventTraceStateService eventTraceStateService;

  public EventCountAndTracesImportJob(final EventTraceStateService eventTraceStateService,
                                      final Runnable callback) {
    super(callback);
    this.eventTraceStateService = eventTraceStateService;
  }

  @Override
  protected void persistEntities(final List<EventResponseDto> eventDtos) {
    eventTraceStateService.updateTracesAndCountsForEvents(eventDtos);
  }
}
