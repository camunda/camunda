/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import java.util.List;
import org.camunda.optimize.dto.optimize.importing.EventProcessGatewayDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.es.schema.index.events.EventProcessInstanceIndexES;
import org.camunda.optimize.service.db.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

public class EventProcessInstanceDatabaseImportJob
    extends DatabaseImportJob<EventProcessInstanceDto> {

  private final EventProcessInstanceWriter eventProcessInstanceWriter;
  private final List<EventProcessGatewayDto> gatewayLookup;
  private final EventProcessPublishStateDto eventProcessPublishStateDto;

  public EventProcessInstanceDatabaseImportJob(
      final EventProcessInstanceWriter eventProcessInstanceWriter,
      final Runnable callback,
      final DatabaseClient databaseClient,
      final List<EventProcessGatewayDto> gatewayLookup,
      final EventProcessPublishStateDto eventProcessPublishStateDto) {
    super(callback, databaseClient);
    this.eventProcessInstanceWriter = eventProcessInstanceWriter;
    this.gatewayLookup = gatewayLookup;
    this.eventProcessPublishStateDto = eventProcessPublishStateDto;
  }

  @Override
  protected void persistEntities(List<EventProcessInstanceDto> newOptimizeEntities) {
    final String index =
        new EventProcessInstanceIndexES(eventProcessPublishStateDto.getId()).getIndexName();
    eventProcessInstanceWriter.importProcessInstances(index, newOptimizeEntities, gatewayLookup);
  }
}
