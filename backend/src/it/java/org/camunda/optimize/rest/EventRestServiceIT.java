/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.event.EventCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventCountSuggestionsRequestDto;
import org.camunda.optimize.dto.optimize.query.event.EventDto;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.dto.optimize.query.event.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_NAME;

public class EventRestServiceIT extends AbstractIT {

  private static final String START_EVENT_ID = "startEventID";
  private static final String FIRST_TASK_ID = "taskID_1";
  private static final String SECOND_TASK_ID = "taskID_2";
  private static final String THIRD_TASK_ID = "taskID_3";
  private static final String FOURTH_TASK_ID = "taskID_4";
  private static final String END_EVENT_ID = "endEventId";

  private static final Random RANDOM = new Random();

  private static final List<EventDto> backendKetchupEvents =
    createEventDtoListWithProperties("backend", "ketchup", "signup-event", 4);
  private static final List<EventDto> frontendMayoEvents =
    createEventDtoListWithProperties("frontend", "mayonnaise", "registered_event", 2);
  private static final List<EventDto> managementBbqEvents =
    createEventDtoListWithProperties("management", "BBQ_sauce", "onboarded_event", 1);
  private static final List<EventDto> ketchupMayoEvents =
    createEventDtoListWithProperties("ketchup", "mayonnaise", "blacklisted_event", 2);
  private static final List<EventDto> backendMayoEvents =
    createEventDtoListWithProperties("BACKEND", "mayonnaise", "ketchupevent", 2);
  private static final List<EventDto> allEventDtos =
    Stream.of(backendKetchupEvents, frontendMayoEvents, managementBbqEvents, ketchupMayoEvents, backendMayoEvents)
    .flatMap(Collection::stream)
    .collect(toList());

  private static String simpleDiagramXml;

  @BeforeAll
  public static void setup() {
    simpleDiagramXml = createProcessDefinitionXml();
  }

  @BeforeEach
  public void init() {
    ingestEvents();
  }

  @Test
  public void getEventCounts_usingGETendpoint() {
    // when
    List<EventCountDto> eventCountDtos = createGetEventCountsQueryWithRequestParameters(new EventCountRequestDto())
      .executeAndReturnList(EventCountDto.class, 200);

    // then all events are sorted using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(backendKetchupEvents, false),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(frontendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false),
        toEventCountDto(managementBbqEvents, false)
      );
  }

  @Test
  public void getEventCounts_usingGETendpoint_usingSearchTermAndSortAndOrderParameters() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .searchTerm("etch")
      .orderBy("eventName")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createGetEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then matching events are returned with ordering parameters respected
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(backendKetchupEvents, false),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false)
      );
  }

  @Test
  public void getEventCounts() {
    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(new EventCountRequestDto())
      .executeAndReturnList(EventCountDto.class, 200);

    // then all events are sorted using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(backendKetchupEvents, false),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(frontendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false),
        toEventCountDto(managementBbqEvents, false)
      );
  }

  @Test
  public void getEventCounts_noAuthentication() {
    // when
    Response response = createPostEventCountsQueryWithRequestParameters(new EventCountRequestDto())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  public void getEventCounts_usingSearchTerm() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder().searchTerm("etch").build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then matching event counts are return using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(backendKetchupEvents, false),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false)
      );
  }

  @ParameterizedTest(name = "exact or prefix match are returned with search term {0}")
  @ValueSource(strings = {"registered_ev", "registered_event", "regISTERED_event"})
  public void getEventCounts_usingSearchTermLongerThanNGramMax(String searchTerm) {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder().searchTerm(searchTerm).build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then matching event counts are return using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(1)
      .containsExactly(toEventCountDto(frontendMayoEvents, false));
  }

  @Test
  public void getEventCounts_usingSortAndOrderParameters() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .orderBy("source")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all matching event counts are return using sort and ordering provided
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(ketchupMayoEvents, false),
        toEventCountDto(frontendMayoEvents, false),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(backendKetchupEvents, false),
        toEventCountDto(managementBbqEvents, false)
      );
  }

  @Test
  public void getEventCounts_usingSortAndOrderParametersMatchingDefault() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .orderBy("group")
      .sortOrder(SortOrder.ASC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all matching event counts are return using sort and ordering provided
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(backendKetchupEvents, false),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(frontendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false),
        toEventCountDto(managementBbqEvents, false)
      );
  }

  @Test
  public void getEventCounts_usingInvalidSortAndOrderParameters() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .orderBy("notAField")
      .build();

    // when
    Response response = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto).execute();

    // then validation exception is thrown
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void getEventCounts_usingSearchTermAndSortAndOrderParameters() {
    // given
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .searchTerm("etch")
      .orderBy("eventName")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestParameters(eventCountRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then matching events are returned with ordering parameters respected
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(backendKetchupEvents, false),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvents(backendKetchupEvents);
    EventTypeDto unmappedEventOne = eventTypeFromEvents(frontendMayoEvents);
    EventSequenceCountDto previousRelevantEventSequence = createEventSequence(previousMappedEvent, unmappedEventOne);

    EventTypeDto nextMappedEvent = eventTypeFromEvents(ketchupMayoEvents);
    EventTypeDto unmappedEventTwo = eventTypeFromEvents(backendMayoEvents);
    EventSequenceCountDto nextRelevantEventSequence = createEventSequence(unmappedEventTwo, nextMappedEvent);

    storeSequencesInElasticsearch(Arrays.asList(previousRelevantEventSequence, nextRelevantEventSequence));

    // Suggestions request for flow node with event mapped before and after
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        FIRST_TASK_ID, createEventMappingDto(null, previousMappedEvent),
        THIRD_TASK_ID, createEventMappingDto(nextMappedEvent, null)
      ))
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then events that are sequenced with mapped events are first and marked as suggested
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(backendMayoEvents, true),
        toEventCountDto(frontendMayoEvents, true),
        toEventCountDto(managementBbqEvents, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist_onlyNearestNeighboursConsidered() {
    // given
    EventTypeDto nearestNextMappedEvent = eventTypeFromEvents(backendKetchupEvents);
    EventTypeDto furthestNextMappedEvent = eventTypeFromEvents(frontendMayoEvents);
    EventTypeDto unmappedEventOne = eventTypeFromEvents(ketchupMayoEvents);
    EventSequenceCountDto nearestRelevantEventSequence = createEventSequence(unmappedEventOne, nearestNextMappedEvent);
    EventTypeDto unmappedEventTwo = eventTypeFromEvents(backendMayoEvents);
    EventSequenceCountDto furthestNextRelevantEventSequence = createEventSequence(unmappedEventTwo, furthestNextMappedEvent);

    storeSequencesInElasticsearch(
      Arrays.asList(nearestRelevantEventSequence, furthestNextRelevantEventSequence));

    // Suggestions request for flow node with events mapped after in two nearest neighbours
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(FIRST_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        SECOND_TASK_ID, createEventMappingDto(nearestNextMappedEvent, null),
        THIRD_TASK_ID, createEventMappingDto(furthestNextMappedEvent, null)
      ))
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then only the event in sequence before closest neighbour is suggested, non-suggestions use default ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(ketchupMayoEvents, true),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(managementBbqEvents, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist_alreadyMappedEventsAreOmitted() {
    // given
    EventTypeDto nextMappedEvent = eventTypeFromEvents(backendKetchupEvents);
    EventTypeDto otherMappedEvent = eventTypeFromEvents(frontendMayoEvents);
    EventSequenceCountDto eventSequence = createEventSequence(otherMappedEvent, nextMappedEvent);

    storeSequencesInElasticsearch(Collections.singletonList(eventSequence));

    // Suggestions request for flow node with event mapped after
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        FIRST_TASK_ID, createEventMappingDto(otherMappedEvent, null),
        THIRD_TASK_ID, createEventMappingDto(nextMappedEvent, null)
      ))
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then no suggestions returned as matching sequence event has already been mapped
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false),
        toEventCountDto(managementBbqEvents, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndMappingsExist_usingCustomSorting() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvents(backendKetchupEvents);
    EventTypeDto unmappedEvent = eventTypeFromEvents(frontendMayoEvents);
    EventSequenceCountDto eventSequence = createEventSequence(previousMappedEvent, unmappedEvent);

    storeSequencesInElasticsearch(Collections.singletonList(eventSequence));

    // Suggestions request for flow node with event mapped before
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .build();
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .orderBy("source")
      .sortOrder(SortOrder.DESC)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestAndSuggestionsParams(
      eventCountRequestDto, eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then counts that are not suggestions respect custom ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(frontendMayoEvents, true),
        toEventCountDto(ketchupMayoEvents, false),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(managementBbqEvents, false)
        );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist_usingSearchTerm() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvents(backendKetchupEvents);
    EventTypeDto unmappedEvent = eventTypeFromEvents(frontendMayoEvents);
    EventSequenceCountDto eventSequence = createEventSequence(previousMappedEvent, unmappedEvent);

    storeSequencesInElasticsearch(Collections.singletonList(eventSequence));

    // Suggestions request for flow node with event mapped before
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .build();
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder().searchTerm("ayon").build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestAndSuggestionsParams(
      eventCountRequestDto, eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then only results matching search term are returned
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(frontendMayoEvents, true),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndMappingsExist_searchTermDoesNotMatchSuggestions() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvents(backendKetchupEvents);
    EventTypeDto unmappedEvent = eventTypeFromEvents(frontendMayoEvents);
    EventSequenceCountDto eventSequence = createEventSequence(previousMappedEvent, unmappedEvent);

    storeSequencesInElasticsearch(Collections.singletonList(eventSequence));

    // Suggestions request for flow node with event mapped before
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .build();
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder().searchTerm("etch").build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithRequestAndSuggestionsParams(
      eventCountRequestDto, eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then suggested and non-suggested counts are filtered out by search term
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(2)
      .containsExactly(
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeNoMappingsExist() {
    // Suggestions request for flow node with event mapped after
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all events are returned with no suggested using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(5)
      .containsExactly(
        toEventCountDto(backendKetchupEvents, false),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(frontendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false),
        toEventCountDto(managementBbqEvents, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeMappingsExist_mappingsGreaterThanConsideredDistance() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvents(backendKetchupEvents);
    EventTypeDto unmappedEventOne = eventTypeFromEvents(frontendMayoEvents);
    EventSequenceCountDto existingEventSequence = createEventSequence(previousMappedEvent, unmappedEventOne);

    storeSequencesInElasticsearch(Collections.singletonList(existingEventSequence));

    // Suggestions request for flow node with event mapped before but greater than considered distance of 2
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(FOURTH_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(previousMappedEvent, null)))
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all unmapped events are returned with no suggested using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(4)
      .containsExactly(
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(frontendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false),
        toEventCountDto(managementBbqEvents, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeWithStartAndEndMappings_onlyClosestConsidered() {
    // given
    EventTypeDto previousMappedEndEvent = eventTypeFromEvents(backendKetchupEvents);
    EventTypeDto previousMappedStartEvent = eventTypeFromEvents(frontendMayoEvents);
    EventTypeDto unmappedEventOne = eventTypeFromEvents(managementBbqEvents);
    EventTypeDto unmappedEventTwo = eventTypeFromEvents(ketchupMayoEvents);
    EventSequenceCountDto eventSequenceToEnd = createEventSequence(previousMappedEndEvent, unmappedEventOne);
    EventSequenceCountDto eventSequenceToStart = createEventSequence(previousMappedStartEvent, unmappedEventTwo);

    storeSequencesInElasticsearch(Arrays.asList(eventSequenceToEnd, eventSequenceToStart));

    // Suggestions request for flow node with event mapped before as start and end event
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(THIRD_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(SECOND_TASK_ID, createEventMappingDto(previousMappedStartEvent, previousMappedEndEvent)))
      .build();

    // when
    List<EventCountDto> eventCountDtos = createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 200);

    // then all unmapped events are returned and only event sequenced to the mapped end event is suggested
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        toEventCountDto(managementBbqEvents, true),
        toEventCountDto(backendMayoEvents, false),
        toEventCountDto(ketchupMayoEvents, false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForInvalidTargetNode() {
    // Suggestions request for flow node with ID that doesn't exist within xml
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId("some_unknown_id")
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(eventTypeFromEvents(backendKetchupEvents), null)))
      .build();

    // then the correct status code is returned
    createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 400);
  }

  @Test
  public void getEventCounts_withSuggestionsAndMappingsThatDoNotMatchXmlProvided() {
    // Suggestions request with mappings for node ID that doesn't exist within xml
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of("some_unknown_id", createEventMappingDto(eventTypeFromEvents(backendKetchupEvents), null)))
      .build();

    // then the correct status code is returned
    createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 400);
  }

  @ParameterizedTest(name = "event counts with suggestions is invalid with xml: {0}")
  @MethodSource("invalidParameters")
  public void getEventCounts_withSuggestionsAndInvalidXmlProvided(String xml) {
    // Suggestions request for node ID and no xml provided
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(xml)
      .mappings(Collections.emptyMap())
      .build();

    // then the correct status code is returned
    createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 400);
  }

  @ParameterizedTest(name = "event counts with suggestions is invalid with targetFlowNodeId: {0}")
  @MethodSource("invalidParameters")
  public void getEventCounts_withSuggestionsAndInvalidFlowNodeIdProvided(String flowNodeId) {
    // Suggestions request for invalid flowNodeId
    EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto = EventCountSuggestionsRequestDto.builder()
      .targetFlowNodeId(flowNodeId)
      .xml(simpleDiagramXml)
      .mappings(Collections.emptyMap())
      .build();

    // then the correct status code is returned
    createPostEventCountsQueryWithSuggestionsParameters(
      eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountDto.class, 400);
  }

  private static Stream<String> invalidParameters() {
    return Stream.of("", "   ", null);
  }

  private EventTypeDto eventTypeFromEvents(List<EventDto> events) {
    EventDto eventFromList = events.get(0);
    return EventTypeDto.builder()
      .group(eventFromList.getGroup())
      .source(eventFromList.getSource())
      .eventName(eventFromList.getEventName())
      .build();
  }

  private EventCountDto toEventCountDto(List<EventDto> events, boolean suggested) {
    EventDto eventFromList = events.get(0);
    return EventCountDto.builder()
      .group(eventFromList.getGroup())
      .source(eventFromList.getSource())
      .eventName(eventFromList.getEventName())
      .count((long) events.size())
      .suggested(suggested)
      .build();
  }

  private EventSequenceCountDto createEventSequence(final EventTypeDto sourceEvent,
                                                    final EventTypeDto targetEvent) {
    return EventSequenceCountDto.builder()
      .sourceEvent(sourceEvent)
      .targetEvent(targetEvent)
      .count(20L)
      .build();
  }

  private EventMappingDto createEventMappingDto(EventTypeDto startEventDto, EventTypeDto endEventDto) {
    return EventMappingDto.builder()
      .start(startEventDto)
      .end(endEventDto)
      .build();
  }

  private void storeSequencesInElasticsearch(final List<EventSequenceCountDto> nextRelevantEventSequences) {
    nextRelevantEventSequences.forEach(
      eventSequenceCountDto -> elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
        EVENT_SEQUENCE_COUNT_INDEX_NAME,
        eventSequenceCountDto.getId(),
        eventSequenceCountDto
      )
    );
  }

  private void ingestEvents() {
    embeddedOptimizeExtension.getRequestExecutor()
      .buildIngestEventBatch(
        allEventDtos,
        embeddedOptimizeExtension.getConfigurationService()
          .getIngestionConfiguration()
          .getApiSecret()
      )
      .execute();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private static List<EventDto> createEventDtoListWithProperties(String group, String source, String eventName,
                                                                 int quantity) {
    return IntStream.range(0, quantity)
      .mapToObj(operand -> createRandomEventDtoPropertiesBuilder()
        .group(group)
        .source(source)
        .eventName(eventName)
        .build())
      .collect(toList());
  }

  private static EventDto.EventDtoBuilder createRandomEventDtoPropertiesBuilder() {
    return EventDto.builder()
      .id(UUID.randomUUID().toString())
      .timestamp(System.currentTimeMillis())
      .traceId(RandomStringUtils.randomAlphabetic(10))
      .duration(Math.abs(RANDOM.nextLong()))
      .data(ImmutableMap.of(
        RandomStringUtils.randomAlphabetic(5), RANDOM.nextInt(),
        RandomStringUtils.randomAlphabetic(5), RANDOM.nextBoolean(),
        RandomStringUtils.randomAlphabetic(5), RandomStringUtils.randomAlphabetic(5)
      ));
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

  private OptimizeRequestExecutor createPostEventCountsQueryWithSuggestionsParameters(EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto) {
    return createPostEventCountsQueryWithRequestAndSuggestionsParams(null, eventCountSuggestionsRequestDto);
  }

  private OptimizeRequestExecutor createPostEventCountsQueryWithRequestParameters(EventCountRequestDto eventCountRequestDto) {
    return createPostEventCountsQueryWithRequestAndSuggestionsParams(eventCountRequestDto, null);
  }

  private OptimizeRequestExecutor createPostEventCountsQueryWithRequestAndSuggestionsParams(EventCountRequestDto eventCountRequestDto,
                                                                                            EventCountSuggestionsRequestDto eventCountSuggestionsRequestDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildPostEventCountRequest(eventCountRequestDto, eventCountSuggestionsRequestDto);
  }

  private OptimizeRequestExecutor createGetEventCountsQueryWithRequestParameters(EventCountRequestDto eventCountRequestDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGetEventCountRequest(eventCountRequestDto);
  }

}
