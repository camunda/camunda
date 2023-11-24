/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.service.db.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

import java.util.List;

public class EventProcessInstanceDatabaseImportJob extends DatabaseImportJob<EventProcessInstanceDto> {

  private final EventProcessInstanceWriter eventProcessInstanceWriter;

  public EventProcessInstanceDatabaseImportJob(final EventProcessInstanceWriter eventProcessInstanceWriter,
                                               final Runnable callback) {
    super(callback);
    this.eventProcessInstanceWriter = eventProcessInstanceWriter;
  }

  @Override
  protected void persistEntities(List<EventProcessInstanceDto> newOptimizeEntities) {
    eventProcessInstanceWriter.importProcessInstances(newOptimizeEntities);
  }

}
