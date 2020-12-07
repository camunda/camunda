/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.Collections;

public abstract class AbstractEventRestServiceRolloverIT extends AbstractEventProcessIT {

  protected static final String TIMESTAMP = DeletableEventDto.Fields.timestamp;
  protected static final String GROUP = DeletableEventDto.Fields.group;

  protected CloudEventRequestDto impostorSabotageNav = createEventDtoWithProperties(
    "impostors",
    "navigationRoom",
    "sabotage",
    Instant.now()
  );

  protected CloudEventRequestDto impostorMurderedMedBay = createEventDtoWithProperties(
    "impostors",
    "medBay",
    "murderedNormie",
    Instant.now().plusSeconds(1)
  );

  protected CloudEventRequestDto normieTaskNav = createEventDtoWithProperties(
    "normie",
    "navigationRoom",
    "finishedTask",
    Instant.now().plusSeconds(2)
  );

  @BeforeEach
  public void cleanUpEventIndices() {
    elasticSearchIntegrationTestExtension.deleteAllExternalEventIndices();
    embeddedOptimizeExtension.getElasticSearchSchemaManager().createOrUpdateOptimizeIndex(
      embeddedOptimizeExtension.getOptimizeElasticClient(),
      new EventIndex()
    );
    embeddedOptimizeExtension.getConfigurationService().getEventIndexRolloverConfiguration().setMaxIndexSizeGB(0);
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  protected void ingestEventAndRolloverIndex(final CloudEventRequestDto cloudEventRequestDto) {
    eventClient.ingestEventBatch(Collections.singletonList(cloudEventRequestDto));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.getEventIndexRolloverService().triggerRollover();
  }

  protected CloudEventRequestDto createEventDtoWithProperties(final String group,
                                                              final String source,
                                                              final String type,
                                                              final Instant timestamp) {
    return eventClient.createCloudEventDto()
      .toBuilder()
      .group(group)
      .source(source)
      .type(type)
      .time(timestamp)
      .build();
  }

}
