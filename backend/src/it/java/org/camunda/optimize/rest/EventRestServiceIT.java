/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.bpm.engine.ActivityTypes;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.event.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.CloudEventDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.events.CamundaEventService.EVENT_SOURCE_CAMUNDA;
import static org.camunda.optimize.service.events.CamundaEventService.PROCESS_END_TYPE;
import static org.camunda.optimize.service.events.CamundaEventService.PROCESS_START_TYPE;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaProcessInstanceEndEventSuffix;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaProcessInstanceStartEventSuffix;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaTaskEndEventSuffix;
import static org.camunda.optimize.service.events.CamundaEventService.applyCamundaTaskStartEventSuffix;

public class EventRestServiceIT extends AbstractIT {

  private static final String CAMUNDA_START_EVENT = ActivityTypes.START_EVENT;
  private static final String CAMUNDA_END_EVENT = ActivityTypes.END_EVENT_NONE;
  private static final String CAMUNDA_USER_TASK = ActivityTypes.TASK_USER_TASK;
  private static final String CAMUNDA_SERVICE_TASK = ActivityTypes.TASK_SERVICE;

  private static final String START_EVENT_ID = "startEventID";
  private static final String FIRST_TASK_ID = "taskID_1";
  private static final String SECOND_TASK_ID = "taskID_2";
  private static final String THIRD_TASK_ID = "taskID_3";
  private static final String FOURTH_TASK_ID = "taskID_4";
  private static final String END_EVENT_ID = "endEventID";

  private CloudEventDto backendKetchupEvent = createEventDtoWithProperties("backend", "ketchup", "signup-event");
  private CloudEventDto frontendMayoEvent = createEventDtoWithProperties("frontend", "mayonnaise", "registered_event");
  private CloudEventDto managementBbqEvent = createEventDtoWithProperties("management", "BBQ_sauce", "onboarded_event");
  private CloudEventDto ketchupMayoEvent = createEventDtoWithProperties("ketchup", "mayonnaise", "blacklisted_event");
  private CloudEventDto backendMayoEvent = createEventDtoWithProperties("BACKEND", "mayonnaise", "ketchupevent");
  private CloudEventDto nullSubjectEvent = createEventDtoWithProperties(null, "another", "ketchupevent");

  private final List<CloudEventDto> eventTraceOne = createTraceFromEventList(
    "traceIdOne",
    Arrays.asList(
      backendKetchupEvent, frontendMayoEvent, managementBbqEvent, ketchupMayoEvent, backendMayoEvent, nullSubjectEvent
    )
  );
  private final List<CloudEventDto> eventTraceTwo = createTraceFromEventList(
    "traceIdTwo",
    Arrays.asList(
      backendKetchupEvent, frontendMayoEvent, ketchupMayoEvent, backendMayoEvent, nullSubjectEvent
    )
  );
  private final List<CloudEventDto> eventTraceThree = createTraceFromEventList(
    "traceIdThree", Arrays.asList(backendKetchupEvent, backendMayoEvent)
  );
  private final List<CloudEventDto> eventTraceFour = createTraceFromEventList(
    "traceIdFour", Collections.singletonList(backendKetchupEvent)
  );

  private final List<CloudEventDto> allEventDtos =
    Stream.of(eventTraceOne, eventTraceTwo, eventTraceThree, eventTraceFour)
      .flatMap(Collection::stream)
      .collect(toList());

  private static String simpleDiagramXml;

  @BeforeAll
  public static void setup() {
    simpleDiagramXml = createProcessDefinitionXml();
  }

  @BeforeEach
  public void init() {
    embeddedOptimizeExtension.getConfigurationService().getEventBasedProcessConfiguration().setEnabled(true);
    eventClient.ingestEventBatch(allEventDtos);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.processEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  @Test
  public void getEventCounts_noSources_noResults() {
    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(null, null)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos).hasSize(0);
  }

  @Test
  public void getEventCounts_externalOnly() {
    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequestExternalEventsOnly()
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then all events are sorted using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        toEventCountDto(nullSubjectEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_camundaOnly_startEndEvents() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<EventCountDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(definitionKey, EventScopeType.START_END, ImmutableList.of("1"))
        .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos).containsExactlyInAnyOrder(
      createStartEventCountDto(definitionKey),
      createEndEventCountDto(definitionKey)
    );
  }

  @Test
  public void getEventCounts_camundaOnly_processInstanceEvents() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<EventCountDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(definitionKey, EventScopeType.PROCESS_INSTANCE,
                                                    ImmutableList.of("1")
      )
        .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos).containsExactlyInAnyOrder(
      createProcessInstanceStartEventCountDto(definitionKey),
      createProcessInstanceEndEventCount(definitionKey)
    );
  }

  @Test
  public void getEventCounts_camundaOnly_allEvents() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<EventCountDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(definitionKey, EventScopeType.ALL, ImmutableList.of("1"))
        .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createProcessInstanceStartEventCountDto(definitionKey),
        createStartEventCountDto(definitionKey),
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK),
        createEndEventCountDto(definitionKey),
        createProcessInstanceEndEventCount(definitionKey)
      );
  }

  @Test
  public void getEventCounts_camundaOnly_allEvents_specificVersion() {
    // given
    final String definitionKey = "myProcess";
    // V1 with userTask
    deployAndStartUserTaskProcess(definitionKey);
    // V2 with serviceTask
    deployAndStartServiceTaskProcess(definitionKey);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<EventCountDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(definitionKey, EventScopeType.ALL, ImmutableList.of("1"))
        .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createProcessInstanceStartEventCountDto(definitionKey),
        createStartEventCountDto(definitionKey),
        // only V1 tasks are expected
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK),
        createEndEventCountDto(definitionKey),
        createProcessInstanceEndEventCount(definitionKey)
      );
  }

  @ParameterizedTest(name = "get all camunda events of the latest version if version selection is {0}")
  @MethodSource("multipleVersionCases")
  public void getEventCounts_camundaOnly_allEvents_multipleVersions_latestWins(final List<String> versions) {
    // given
    final String definitionKey = "myProcess";
    // V1 with userTask
    deployAndStartUserTaskProcess(definitionKey);
    // V2 with serviceTask
    deployAndStartServiceTaskProcess(definitionKey);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<EventCountDto> eventCountDtos =
      createPostEventCountsRequestCamundaSourceOnly(definitionKey, EventScopeType.ALL, versions)
        .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createProcessInstanceStartEventCountDto(definitionKey),
        createStartEventCountDto(definitionKey),
        // we only expect the events from the latest version in these cases
        createTaskStartEventCountDto(definitionKey, CAMUNDA_SERVICE_TASK),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_SERVICE_TASK),
        createEndEventCountDto(definitionKey),
        createProcessInstanceEndEventCount(definitionKey)
      );
  }

  @Test
  public void getEventCounts_camundaOnly_allEvents_specificTenant() {
    // given
    final String definitionKey = "myProcess";
    final String tenantId1 = "tenant1";
    final String tenantId2 = "tenant2";
    // V1 for tenant1
    deployAndStartUserTaskProcess(definitionKey, tenantId1);
    // V1 for tenant2
    deployAndStartServiceTaskProcess(definitionKey, tenantId2);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<EventCountDto> eventCountDtos =
      createPostEventCountsRequest(Lists.newArrayList(
        EventSourceEntryDto.builder()
          .type(EventSourceType.CAMUNDA)
          .processDefinitionKey(definitionKey)
          .versions(ImmutableList.of("1"))
          .eventScope(EventScopeType.ALL)
          .tenants(ImmutableList.of(tenantId2))
          .build()
      )).executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactlyInAnyOrder(
        createProcessInstanceStartEventCountDto(definitionKey),
        createStartEventCountDto(definitionKey),
        // only tenant2 tasks are expected
        createTaskStartEventCountDto(definitionKey, CAMUNDA_SERVICE_TASK),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_SERVICE_TASK),
        createEndEventCountDto(definitionKey),
        createProcessInstanceEndEventCount(definitionKey)
      );
  }

  @Test
  public void getEventCounts_camundaAndExternal_allEvents() {
    // given
    final String definitionKey = "myProcess";
    deployAndStartUserTaskProcess(definitionKey);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<EventCountDto> eventCountDtos =
      createPostEventCountsRequest(
        ImmutableList.of(
          createExternalEventSourceEntryDto(),
          createCamundaEventSourceEntryDto(definitionKey, EventScopeType.ALL, ImmutableList.of("1"))
        )
      ).executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(eventCountDtos)
      .containsExactly(
        toEventCountDto(nullSubjectEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false),
        createProcessInstanceEndEventCount(definitionKey),
        createProcessInstanceStartEventCountDto(definitionKey),
        createEndEventCountDto(definitionKey),
        createStartEventCountDto(definitionKey),
        createTaskEndEventCountDto(definitionKey, CAMUNDA_USER_TASK),
        createTaskStartEventCountDto(definitionKey, CAMUNDA_USER_TASK)
      );
  }

  @Test
  public void getEventCounts_noAuthentication() {
    // when
    Response response = createPostEventCountsRequestExternalEventsOnly(new EventCountSearchRequestDto())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getEventCounts_usingSearchTerm() {
    // given
    EventCountSearchRequestDto eventCountRequestDto = EventCountSearchRequestDto.builder().searchTerm("etch").build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequestExternalEventsOnly(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then matching event counts are return using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(nullSubjectEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false)
      );
  }

  @ParameterizedTest(name = "exact or prefix match are returned with search term {0}")
  @ValueSource(strings = {"registered_ev", "registered_event", "regISTERED_event"})
  public void getEventCounts_usingSearchTermLongerThanNGramMax(String searchTerm) {
    // given
    EventCountSearchRequestDto eventCountRequestDto = EventCountSearchRequestDto.builder()
      .searchTerm(searchTerm)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequestExternalEventsOnly(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then matching event counts are return using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(1)
      .containsExactly(toEventCountDto(frontendMayoEvent, 2L, false));
  }

  @Test
  public void getEventCounts_usingSortAndOrderParameters() {
    // given
    EventCountSearchRequestDto eventCountRequestDto = EventCountSearchRequestDto.builder()
      .orderBy("source")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequestExternalEventsOnly(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then all matching event counts are return using sort and ordering provided
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(managementBbqEvent, 1L, false),
        toEventCountDto(nullSubjectEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts_usingSortAndOrderParametersMatchingDefault() {
    // given
    EventCountSearchRequestDto eventCountRequestDto = EventCountSearchRequestDto.builder()
      .orderBy("group")
      .sortOrder(SortOrder.ASC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequestExternalEventsOnly(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then all matching event counts are return using sort and ordering provided
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        toEventCountDto(nullSubjectEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_usingInvalidSortAndOrderParameters() {
    // given
    EventCountSearchRequestDto eventCountRequestDto = EventCountSearchRequestDto.builder()
      .orderBy("notAField")
      .build();

    // when
    Response response = createPostEventCountsRequestExternalEventsOnly(eventCountRequestDto).execute();

    // then validation exception is thrown
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_usingSearchTermAndSortAndOrderParameters() {
    // given
    EventCountSearchRequestDto eventCountRequestDto = EventCountSearchRequestDto.builder()
      .searchTerm("etch")
      .orderBy("eventName")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequestExternalEventsOnly(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then matching events are returned with ordering parameters respected
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(nullSubjectEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false)
      );
  }


  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvent(backendKetchupEvent);
    EventTypeDto nextMappedEvent = eventTypeFromEvent(ketchupMayoEvent);

    // Suggestions request for flow node with event mapped before and after
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        FIRST_TASK_ID, createEventMappingDto(null, previousMappedEvent),
        THIRD_TASK_ID, createEventMappingDto(nextMappedEvent, null)
      ))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then events that are sequenced with mapped events are first and marked as suggested
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(frontendMayoEvent, 2L, true),
        toEventCountDto(managementBbqEvent, 1L, true),
        toEventCountDto(nullSubjectEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExistWithNullFields() {
    // given
    EventTypeDto nextMappedEventWithNullProperties = eventTypeFromEvent(nullSubjectEvent);

    // Suggestions request for flow node with event mapped before and after
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(THIRD_TASK_ID, createEventMappingDto(null, nextMappedEventWithNullProperties)))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then events that are sequenced with mapped events are first and marked as suggested
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist_onlyNearestNeighboursConsidered() {
    // given
    EventTypeDto nearestNextMappedEvent = eventTypeFromEvent(ketchupMayoEvent);
    EventTypeDto furthestNextMappedEvent = eventTypeFromEvent(backendMayoEvent);

    // Suggestions request for flow node with events mapped after in two nearest neighbours
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(FIRST_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        SECOND_TASK_ID, createEventMappingDto(nearestNextMappedEvent, null),
        THIRD_TASK_ID, createEventMappingDto(furthestNextMappedEvent, null)
      ))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then only the event in sequence before closest neighbour is suggested, non-suggestions use default ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(frontendMayoEvent, 2L, true),
        toEventCountDto(managementBbqEvent, 1L, true),
        toEventCountDto(nullSubjectEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist_alreadyMappedEventsAreOmitted() {
    // given
    EventTypeDto nextMappedEvent = eventTypeFromEvent(nullSubjectEvent);
    EventTypeDto otherMappedEvent = eventTypeFromEvent(backendMayoEvent);

    // Suggestions request for flow node with event mapped after
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        FIRST_TASK_ID, createEventMappingDto(otherMappedEvent, null),
        THIRD_TASK_ID, createEventMappingDto(nextMappedEvent, null)
      ))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then no suggestions returned as matching sequence event has already been mapped
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAlreadyMapped_alreadyMappedEventForTargetNotExcluded() {
    // given
    EventTypeDto mappedEvent = eventTypeFromEvent(backendMayoEvent);
    EventTypeDto otherMappedEvent = eventTypeFromEvent(nullSubjectEvent);

    // Suggestions request for already mapped flow node and with event mapped after
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(FIRST_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        FIRST_TASK_ID, createEventMappingDto(mappedEvent, null),
        SECOND_TASK_ID, createEventMappingDto(otherMappedEvent, null)
      ))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then event count list contains suggestions and already mapped target event is included
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndMappingsExist_usingCustomSorting() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvent(backendKetchupEvent);

    // Suggestions request for flow node with event mapped before
    EventCountRequestDto eventCountSuggestionsRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();
    EventCountSearchRequestDto eventCountRequestDto = EventCountSearchRequestDto.builder()
      .orderBy("source")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(
      eventCountRequestDto, eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then counts that are not suggestions respect custom ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(frontendMayoEvent, 2L, true),
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false),
        toEventCountDto(nullSubjectEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist_usingSearchTerm() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvent(backendKetchupEvent);

    // Suggestions request for flow node with event mapped before
    EventCountRequestDto eventCountSuggestionsRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();
    EventCountSearchRequestDto eventCountRequestDto = EventCountSearchRequestDto.builder().searchTerm("ayon").build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(
      eventCountRequestDto, eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then only results matching search term are returned
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(frontendMayoEvent, 2L, true),
        toEventCountDto(ketchupMayoEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndMappingsExist_searchTermDoesNotMatchSuggestions() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvent(backendKetchupEvent);

    // Suggestions request for flow node with event mapped before
    EventCountRequestDto eventCountSuggestionsRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();
    EventCountSearchRequestDto eventCountRequestDto = EventCountSearchRequestDto.builder().searchTerm("etch").build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(
      eventCountRequestDto, eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then suggested and non-suggested counts are filtered out by search term
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(nullSubjectEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeNoMappingsExist() {
    // Suggestions request for flow node with event mapped after
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then all events are returned with no suggested using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        toEventCountDto(nullSubjectEvent, 2L, false),
        toEventCountDto(backendKetchupEvent, 4L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeMappingsExist_mappingsGreaterThanConsideredDistance() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvent(backendKetchupEvent);

    // Suggestions request for flow node with event mapped before but greater than considered distance of 2
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(FOURTH_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then all unmapped events are returned with no suggested using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(nullSubjectEvent, 2L, false),
        toEventCountDto(backendMayoEvent, 3L, false),
        toEventCountDto(frontendMayoEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeWithStartAndEndMappings_onlyClosestConsidered() {
    // given
    EventTypeDto previousMappedEndEvent = eventTypeFromEvent(backendKetchupEvent);
    EventTypeDto previousMappedStartEvent = eventTypeFromEvent(frontendMayoEvent);

    // Suggestions request for flow node with event mapped before as start and end event
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(THIRD_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        SECOND_TASK_ID,
        createEventMappingDto(previousMappedStartEvent, previousMappedEndEvent)
      ))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.OK.getStatusCode());

    // then all unmapped events are returned and only event sequenced to the mapped end event is suggested
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(backendMayoEvent, 3L, true),
        toEventCountDto(nullSubjectEvent, 2L, false),
        toEventCountDto(ketchupMayoEvent, 2L, false),
        toEventCountDto(managementBbqEvent, 1L, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForInvalidTargetNode() {
    // Suggestions request for flow node with ID that doesn't exist within xml
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId("some_unknown_id")
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(eventTypeFromEvent(backendKetchupEvent), null)))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // then the correct status code is returned
    createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_withSuggestionsAndMappingsThatDoNotMatchXmlProvided() {
    // Suggestions request with mappings for node ID that doesn't exist within xml
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        "some_unknown_id",
        createEventMappingDto(eventTypeFromEvent(backendKetchupEvent), null)
      ))
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // then the correct status code is returned
    createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest(name = "event counts with suggestions is invalid with xml: {0}")
  @MethodSource("invalidParameters")
  public void getEventCounts_withSuggestionsAndInvalidXmlProvided(String xml) {
    // Suggestions request for node ID and no xml provided
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(xml)
      .mappings(Collections.emptyMap())
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // then the correct status code is returned
    createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest(name = "event counts with suggestions is invalid with targetFlowNodeId: {0}")
  @MethodSource("invalidParameters")
  public void getEventCounts_withSuggestionsAndInvalidFlowNodeIdProvided(String flowNodeId) {
    // Suggestions request for invalid flowNodeId
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(flowNodeId)
      .xml(simpleDiagramXml)
      .mappings(Collections.emptyMap())
      .eventSources(createEventSourcesWithExternalEventsOnly())
      .build();

    // then the correct status code is returned
    createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, Response.Status.BAD_REQUEST.getStatusCode());
  }

  private static Stream<String> invalidParameters() {
    return Stream.of("");
  }

  private EventTypeDto eventTypeFromEvent(CloudEventDto event) {
    return EventTypeDto.builder()
      .group(event.getGroup().orElse(null))
      .source(event.getSource())
      .eventName(event.getType())
      .build();
  }

  private EventCountDto toEventCountDto(CloudEventDto event, Long count, boolean suggested) {
    return EventCountDto.builder()
      .group(event.getGroup().orElse(null))
      .source(event.getSource())
      .eventName(event.getType())
      .count(count)
      .suggested(suggested)
      .build();
  }

  private EventMappingDto createEventMappingDto(EventTypeDto startEventDto, EventTypeDto endEventDto) {
    return EventMappingDto.builder()
      .start(startEventDto)
      .end(endEventDto)
      .build();
  }

  private List<CloudEventDto> createTraceFromEventList(String traceId, List<CloudEventDto> events) {
    AtomicInteger incrementCounter = new AtomicInteger(0);
    Instant currentTimestamp = Instant.now();
    return events.stream()
      .map(event -> createEventDtoWithProperties(event.getGroup().orElse(null), event.getSource(), event.getType()))
      .peek(eventDto -> eventDto.setTraceid(traceId))
      .peek(eventDto -> eventDto.setTime(currentTimestamp.plusSeconds(incrementCounter.getAndIncrement())))
      .collect(toList());
  }

  private CloudEventDto createEventDtoWithProperties(final String subject, final String source, final String type) {
    return eventClient.createCloudEventDto()
      .toBuilder()
      .group(subject)
      .source(source)
      .type(type)
      .build();
  }

  @SneakyThrows
  private static String createProcessDefinitionXml() {
    final BpmnModelInstance bpmnModel = Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent(START_EVENT_ID)
      .userTask(FIRST_TASK_ID)
      .userTask(SECOND_TASK_ID)
      .userTask(THIRD_TASK_ID)
      .userTask(FOURTH_TASK_ID)
      .endEvent(END_EVENT_ID)
      .done();
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, bpmnModel);
    return new String(xmlOutput.toByteArray(), StandardCharsets.UTF_8);
  }

  private OptimizeRequestExecutor createPostEventCountsRequest(
    final EventCountRequestDto eventCountRequestDto) {
    return createPostEventCountsRequest(null, eventCountRequestDto);
  }

  private OptimizeRequestExecutor createPostEventCountsRequestExternalEventsOnly() {
    return createPostEventCountsRequestExternalEventsOnly(null);
  }

  private OptimizeRequestExecutor createPostEventCountsRequestExternalEventsOnly(
    final EventCountSearchRequestDto eventCountSearchRequestDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildPostEventCountRequest(
        eventCountSearchRequestDto,
        EventCountRequestDto.builder().eventSources(createEventSourcesWithExternalEventsOnly()).build()
      );
  }

  private List<EventSourceEntryDto> createEventSourcesWithExternalEventsOnly() {
    return Lists.newArrayList(createExternalEventSourceEntryDto());
  }

  private OptimizeRequestExecutor createPostEventCountsRequestCamundaSourceOnly(final String definitionKey,
                                                                                final EventScopeType eventScope,
                                                                                final List<String> versions) {
    final ArrayList<EventSourceEntryDto> eventSources = Lists.newArrayList(
      createCamundaEventSourceEntryDto(definitionKey, eventScope, versions)
    );
    return createPostEventCountsRequest(eventSources);
  }

  private OptimizeRequestExecutor createPostEventCountsRequest(final List<EventSourceEntryDto> eventSources) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildPostEventCountRequest(EventCountRequestDto.builder().eventSources(eventSources).build());
  }

  private OptimizeRequestExecutor createPostEventCountsRequest(final EventCountSearchRequestDto eventCountRequestDto,
                                                               final EventCountRequestDto eventCountSuggestionsRequestDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildPostEventCountRequest(eventCountRequestDto, eventCountSuggestionsRequestDto);
  }

  private EventSourceEntryDto createExternalEventSourceEntryDto() {
    return EventSourceEntryDto.builder().type(EventSourceType.EXTERNAL).build();
  }

  private EventSourceEntryDto createCamundaEventSourceEntryDto(final String definitionKey,
                                                               final EventScopeType eventScope,
                                                               final List<String> versions) {
    return EventSourceEntryDto.builder()
      .type(EventSourceType.CAMUNDA)
      .processDefinitionKey(definitionKey)
      .versions(versions)
      .eventScope(eventScope)
      .build();
  }

  private EventCountDto createProcessInstanceStartEventCountDto(final String definitionKey) {
    return EventCountDto.builder()
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .eventName(applyCamundaProcessInstanceStartEventSuffix(definitionKey))
      .eventLabel(PROCESS_START_TYPE)
      .build();
  }

  private EventCountDto createProcessInstanceEndEventCount(final String definitionKey) {
    return EventCountDto.builder()
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .eventName(applyCamundaProcessInstanceEndEventSuffix(definitionKey))
      .eventLabel(PROCESS_END_TYPE)
      .build();
  }

  private EventCountDto createStartEventCountDto(final String definitionKey) {
    return EventCountDto.builder()
      .eventName(CAMUNDA_START_EVENT)
      .eventLabel(CAMUNDA_START_EVENT)
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .build();
  }

  private EventCountDto createEndEventCountDto(final String definitionKey) {
    return EventCountDto.builder()
      .eventName(CAMUNDA_END_EVENT)
      .eventLabel(CAMUNDA_END_EVENT)
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .build();
  }

  private EventCountDto createTaskEndEventCountDto(final String definitionKey, final String activityId) {
    return EventCountDto.builder()
      .eventName(applyCamundaTaskEndEventSuffix(activityId))
      .eventLabel(applyCamundaTaskEndEventSuffix(activityId))
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .build();
  }

  private EventCountDto createTaskStartEventCountDto(final String definitionKey, final String activityId) {
    return EventCountDto.builder()
      .eventName(applyCamundaTaskStartEventSuffix(activityId))
      .eventLabel(applyCamundaTaskStartEventSuffix(activityId))
      .source(EVENT_SOURCE_CAMUNDA)
      .group(definitionKey)
      .build();
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcess(final String definitionKey) {
    return deployAndStartUserTaskProcess(definitionKey, null);
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcess(final String definitionKey, final String tenantId) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .startEvent(CAMUNDA_START_EVENT)
      .userTask(CAMUNDA_USER_TASK)
      .endEvent(CAMUNDA_END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(processModel, tenantId);
  }

  private ProcessInstanceEngineDto deployAndStartServiceTaskProcess(final String definitionKey) {
    return deployAndStartServiceTaskProcess(definitionKey, null);
  }

  private ProcessInstanceEngineDto deployAndStartServiceTaskProcess(final String definitionKey, final String tenantId) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .startEvent(CAMUNDA_START_EVENT)
      .serviceTask(CAMUNDA_SERVICE_TASK)
        .camundaExpression("${true}")
      .endEvent(CAMUNDA_END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(processModel);
  }

  private static Stream<List<String>> multipleVersionCases() {
    return Stream.of(
      ImmutableList.of("all"),
      ImmutableList.of("1", "2"),
      ImmutableList.of("2"),
      ImmutableList.of("latest")
    );
  }

}
