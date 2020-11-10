/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess.handler;

import org.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import org.camunda.optimize.service.importing.TimestampBasedImportIndexHandler;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EventProcessInstanceImportSourceIndexHandler
  extends TimestampBasedImportIndexHandler<EventImportSourceDto> {

  private final EventImportSourceDto eventImportSourceDto;

  public EventProcessInstanceImportSourceIndexHandler(final ConfigurationService configurationService,
                                                      final EventImportSourceDto eventImportSourceDto) {
    this.configurationService = configurationService;
    this.eventImportSourceDto = eventImportSourceDto;
    updatePendingLastEntityTimestamp(eventImportSourceDto.getLastImportedEventTimestamp());
  }

  @Override
  public EventImportSourceDto getIndexStateDto() {
    return eventImportSourceDto;
  }

  @Override
  public String getEngineAlias() {
    return ElasticsearchConstants.EVENT_PROCESSING_ENGINE_REFERENCE;
  }

  @Override
  protected void updateLastPersistedEntityTimestamp(final OffsetDateTime timestamp) {
    eventImportSourceDto.setLastImportedEventTimestamp(timestamp);
  }

  @Override
  protected void updateLastImportExecutionTimestamp(final OffsetDateTime timestamp) {
    eventImportSourceDto.setLastImportExecutionTimestamp(timestamp);
  }

}
