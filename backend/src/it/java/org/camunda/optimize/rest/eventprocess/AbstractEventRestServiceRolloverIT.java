/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    ingestionClient.ingestEventBatch(Collections.singletonList(cloudEventRequestDto));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.getEventIndexRolloverService().triggerRollover();
  }

  protected CloudEventRequestDto createEventDtoWithProperties(final String group,
                                                              final String source,
                                                              final String type,
                                                              final Instant timestamp) {
    return ingestionClient.createCloudEventDto()
      .toBuilder()
      .group(group)
      .source(source)
      .type(type)
      .time(timestamp)
      .build();
  }

  protected void assertThatEventsHaveBeenDeleted(final List<EventDto> allSavedEventsBeforeDelete,
                                               final List<String> expectedDeletedEvenIds) {
    assertThat(getAllStoredEvents())
      .hasSize(allSavedEventsBeforeDelete.size() - expectedDeletedEvenIds.size())
      .extracting(EventDto::getId)
      .doesNotContainAnyElementsOf(expectedDeletedEvenIds);
  }

}
