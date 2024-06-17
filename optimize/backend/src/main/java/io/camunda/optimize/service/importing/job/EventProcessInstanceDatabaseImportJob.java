/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.importing.EventProcessGatewayDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.es.schema.index.events.EventProcessInstanceIndexES;
import io.camunda.optimize.service.db.writer.EventProcessInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import java.util.List;

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
