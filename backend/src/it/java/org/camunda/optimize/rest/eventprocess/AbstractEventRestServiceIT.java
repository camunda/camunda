/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.camunda.bpm.engine.ActivityTypes;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public abstract class AbstractEventRestServiceIT extends AbstractEventProcessIT {

  protected static final String CAMUNDA_START_EVENT = ActivityTypes.START_EVENT;
  protected static final String CAMUNDA_END_EVENT = ActivityTypes.END_EVENT_NONE;
  protected static final String CAMUNDA_USER_TASK = ActivityTypes.TASK_USER_TASK;
  protected static final String CAMUNDA_SERVICE_TASK = ActivityTypes.TASK_SERVICE;

  protected static final String START_EVENT_ID = "startEventID";
  protected static final String FIRST_TASK_ID = "taskID_1";
  protected static final String SECOND_TASK_ID = "taskID_2";
  protected static final String THIRD_TASK_ID = "taskID_3";
  protected static final String FOURTH_TASK_ID = "taskID_4";
  protected static final String END_EVENT_ID = "endEventID";

  protected CloudEventRequestDto backendKetchupEvent = createEventDtoWithProperties(
    "backend",
    "ketchup",
    "signup-event"
  );
  protected CloudEventRequestDto frontendMayoEvent = createEventDtoWithProperties(
    "frontend",
    "mayonnaise",
    "registered_event"
  );
  protected CloudEventRequestDto managementBbqEvent = createEventDtoWithProperties(
    "management",
    "BBQ_sauce",
    "onboarded_event"
  );
  protected CloudEventRequestDto ketchupMayoEvent = createEventDtoWithProperties(
    "ketchup",
    "mayonnaise",
    "blacklisted_event"
  );
  protected CloudEventRequestDto backendMayoEvent = createEventDtoWithProperties(
    "BACKEND",
    "mayonnaise",
    "ketchupevent"
  );
  protected CloudEventRequestDto nullGroupEvent = createEventDtoWithProperties(null, "another", "ketchupevent");

  protected final List<CloudEventRequestDto> eventTraceOne = createTraceFromEventList(
    "traceIdOne",
    Arrays.asList(
      backendKetchupEvent, frontendMayoEvent, managementBbqEvent, ketchupMayoEvent, backendMayoEvent, nullGroupEvent
    )
  );
  protected final List<CloudEventRequestDto> eventTraceTwo = createTraceFromEventList(
    "traceIdTwo",
    Arrays.asList(
      backendKetchupEvent, frontendMayoEvent, ketchupMayoEvent, backendMayoEvent, nullGroupEvent
    )
  );
  protected final List<CloudEventRequestDto> eventTraceThree = createTraceFromEventList(
    "traceIdThree", Arrays.asList(backendKetchupEvent, backendMayoEvent)
  );
  protected final List<CloudEventRequestDto> eventTraceFour = createTraceFromEventList(
    "traceIdFour", Collections.singletonList(backendKetchupEvent)
  );

  protected final List<CloudEventRequestDto> allEventDtos =
    Stream.of(eventTraceOne, eventTraceTwo, eventTraceThree, eventTraceFour)
      .flatMap(Collection::stream)
      .collect(toList());

  protected static String simpleDiagramXml;

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    embeddedOptimizeExtension.reloadConfiguration();
    ingestionClient.ingestEventBatch(allEventDtos);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    processEventTracesAndSequences();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected List<CloudEventRequestDto> createTraceFromEventList(String traceId, List<CloudEventRequestDto> events) {
    AtomicInteger incrementCounter = new AtomicInteger(0);
    Instant currentTimestamp = Instant.now();
    return events.stream()
      .map(event -> createEventDtoWithProperties(
        event.getGroup().orElse(null),
        event.getSource(),
        event.getType()
      ).toBuilder().id(event.getId() + traceId).build())
      .peek(eventDto -> eventDto.setTraceid(traceId))
      .peek(eventDto -> eventDto.setTime(currentTimestamp.plusSeconds(incrementCounter.getAndIncrement())))
      .collect(toList());
  }

  private CloudEventRequestDto createEventDtoWithProperties(final String group, final String source,
                                                            final String type) {
    return ingestionClient.createCloudEventDto()
      .toBuilder()
      .group(group)
      .source(source)
      .type(type)
      .build();
  }

  protected void processEventTracesAndSequences() {
    embeddedOptimizeExtension.processEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected void removeAllUserEventProcessAuthorizations() {
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .getAuthorizedUserIds()
      .clear();
  }

}
