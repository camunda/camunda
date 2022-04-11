/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountRequestDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.dto.optimize.rest.sorting.EventCountSorter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventAllGroupsSourceEntry;

public class EventCountRestServiceWithSuggestionsIT extends AbstractEventRestServiceIT {

  @BeforeAll
  public static void setup() {
    simpleDiagramXml = createProcessDefinitionXml();
  }

  @Test
  public void getEventCounts_withSuggestions_invalidBpmnXml() {
    // given
    EventTypeDto previousMappedEvent = eventTypeFromEvent(backendKetchupEvent);
    EventTypeDto nextMappedEvent = eventTypeFromEvent(ketchupMayoEvent);

    // Suggestions request for flow node with event mapped before and after
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml("some invalid BPMN xml")
      .mappings(ImmutableMap.of(
        FIRST_TASK_ID, createEventMappingDto(null, previousMappedEvent),
        THIRD_TASK_ID, createEventMappingDto(nextMappedEvent, null)
      ))
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    Response response = createPostEventCountsRequest(eventCountRequestDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then events that are sequenced with mapped events are first and marked as suggested
    assertThat(eventCountDtos)
      .containsExactly(
        createBackendMayoCountDto(true),
        createFrontendMayoCountDto(true),
        createManagementBbqCountDto(true),
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createKetchupMayoCountDto(false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExistWithNullFields() {
    // given
    EventTypeDto nextMappedEventWithNullProperties = eventTypeFromEvent(nullGroupEvent);

    // Suggestions request for flow node with event mapped before and after
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(THIRD_TASK_ID, createEventMappingDto(null, nextMappedEventWithNullProperties)))
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then events that are sequenced with mapped events are first and marked as suggested
    assertThat(eventCountDtos)
      .containsExactly(
        createBackendMayoCountDto(true),
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createFrontendMayoCountDto(false),
        createKetchupMayoCountDto(false),
        createManagementBbqCountDto(false)
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
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then only the event in sequence before closest neighbour is suggested, non-suggestions use default ordering
    assertThat(eventCountDtos)
      .containsExactly(
        createFrontendMayoCountDto(true),
        createManagementBbqCountDto(true),
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createBackendMayoCountDto(false),
        createKetchupMayoCountDto(false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAndRelevantMappingsExist_alreadyMappedEventsAreNotSuggested() {
    // given
    EventTypeDto firstMappedEvent = eventTypeFromEvent(backendMayoEvent);
    EventTypeDto thirdMappedEvent = eventTypeFromEvent(nullGroupEvent);

    // Suggestions request for flow node with event mapped after
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        FIRST_TASK_ID, createEventMappingDto(firstMappedEvent, null),
        THIRD_TASK_ID, createEventMappingDto(thirdMappedEvent, null)
      ))
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then no suggestions returned as matching sequence event has already been mapped
    assertThat(eventCountDtos)
      .containsExactly(
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createBackendMayoCountDto(false),
        createFrontendMayoCountDto(false),
        createKetchupMayoCountDto(false),
        createManagementBbqCountDto(false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeAlreadyMapped_alreadyMappedEventForTargetStillSuggested() {
    // given
    EventTypeDto mappedEvent = eventTypeFromEvent(backendMayoEvent);
    EventTypeDto otherMappedEvent = eventTypeFromEvent(nullGroupEvent);

    // Suggestions request for already mapped flow node and with event mapped after
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(FIRST_TASK_ID)
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(
        FIRST_TASK_ID, createEventMappingDto(mappedEvent, null),
        SECOND_TASK_ID, createEventMappingDto(otherMappedEvent, null)
      ))
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then event count list contains suggestions and already mapped target event is included
    assertThat(eventCountDtos)
      .containsExactly(
        createBackendMayoCountDto(true),
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createFrontendMayoCountDto(false),
        createKetchupMayoCountDto(false),
        createManagementBbqCountDto(false)
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
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();
    EventCountSorter eventCountSorter = new EventCountSorter("source", SortOrder.DESC);

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(
      eventCountSorter, eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then counts that are not suggestions respect custom ordering
    assertThat(eventCountDtos)
      .containsExactly(
        createFrontendMayoCountDto(true),
        createBackendMayoCountDto(true),
        createKetchupMayoCountDto(false),
        createBackendKetchupCountDto(false),
        createManagementBbqCountDto(false),
        createNullGroupCountDto(false)
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
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest("ayon", eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then only results matching search term are returned
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(3)
      .containsExactly(
        createBackendMayoCountDto(true),
        createFrontendMayoCountDto(true),
        createKetchupMayoCountDto(false)
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
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(
      "etch", eventCountSuggestionsRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then suggested and non-suggested counts are filtered out by search term
    assertThat(eventCountDtos)
      .containsExactly(
        createBackendMayoCountDto(true),
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createKetchupMayoCountDto(false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForValidTargetNodeNoMappingsExist() {
    // Suggestions request for flow node with event mapped after
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml(simpleDiagramXml)
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then result is using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .isNotNull()
      .hasSize(6)
      .containsExactly(
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createBackendMayoCountDto(false),
        createFrontendMayoCountDto(false),
        createKetchupMayoCountDto(false),
        createManagementBbqCountDto(false)
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
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then no suggestions are returned, result is using default group case-insensitive ordering
    assertThat(eventCountDtos)
      .containsExactly(
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createBackendMayoCountDto(false),
        createFrontendMayoCountDto(false),
        createKetchupMayoCountDto(false),
        createManagementBbqCountDto(false)
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
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    List<EventCountResponseDto> eventCountDtos = createPostEventCountsRequest(eventCountRequestDto)
      .executeAndReturnList(EventCountResponseDto.class, Response.Status.OK.getStatusCode());

    // then only event sequenced to the mapped end event is suggested
    assertThat(eventCountDtos)
      .containsExactly(
        createBackendMayoCountDto(true),
        createNullGroupCountDto(false),
        createBackendKetchupCountDto(false),
        createFrontendMayoCountDto(false),
        createKetchupMayoCountDto(false),
        createManagementBbqCountDto(false)
      );
  }

  @Test
  public void getEventCounts_withSuggestionsForInvalidTargetNode() {
    // Suggestions request for flow node with ID that doesn't exist within xml
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId("some_unknown_id")
      .xml(simpleDiagramXml)
      .mappings(ImmutableMap.of(FIRST_TASK_ID, createEventMappingDto(eventTypeFromEvent(backendKetchupEvent), null)))
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    final Response response = createPostEventCountsRequest(eventCountRequestDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    final Response response = createPostEventCountsRequest(eventCountRequestDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_withSuggestionsAndInvalidXmlProvided() {
    // Suggestions request for node ID and no xml provided
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId(SECOND_TASK_ID)
      .xml("")
      .mappings(Collections.emptyMap())
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    final Response response = createPostEventCountsRequest(eventCountRequestDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getEventCounts_withSuggestionsAndInvalidFlowNodeIdProvided() {
    // Suggestions request for invalid flowNodeId
    EventCountRequestDto eventCountRequestDto = EventCountRequestDto.builder()
      .targetFlowNodeId("")
      .xml(simpleDiagramXml)
      .mappings(Collections.emptyMap())
      .eventSources(createEventSourcesWithAllExternalEventsOnly())
      .build();

    // when
    final Response response = createPostEventCountsRequest(eventCountRequestDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private EventTypeDto eventTypeFromEvent(CloudEventRequestDto event) {
    return EventTypeDto.builder()
      .group(event.getGroup().orElse(null))
      .source(event.getSource())
      .eventName(event.getType())
      .build();
  }

  private EventMappingDto createEventMappingDto(EventTypeDto startEventDto, EventTypeDto endEventDto) {
    return EventMappingDto.builder()
      .start(startEventDto)
      .end(endEventDto)
      .build();
  }

  private OptimizeRequestExecutor createPostEventCountsRequest(final EventCountRequestDto eventCountRequestDto) {
    return createPostEventCountsRequest(null, null, eventCountRequestDto);
  }

  private OptimizeRequestExecutor createPostEventCountsRequest(final EventCountSorter eventCountSorter,
                                                               final String searchTerm,
                                                               final EventCountRequestDto eventCountSuggestionsRequestDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildPostEventCountRequest(eventCountSorter, searchTerm, eventCountSuggestionsRequestDto);
  }

  private OptimizeRequestExecutor createPostEventCountsRequest(final String searchTerm,
                                                               final EventCountRequestDto eventCountSuggestionsRequestDto) {
    return createPostEventCountsRequest(null, searchTerm, eventCountSuggestionsRequestDto);
  }

  private OptimizeRequestExecutor createPostEventCountsRequest(final EventCountSorter eventCountSorter,
                                                               final EventCountRequestDto eventCountSuggestionsRequestDto) {
    return createPostEventCountsRequest(eventCountSorter, null, eventCountSuggestionsRequestDto);
  }

  private List<EventSourceEntryDto<?>> createEventSourcesWithAllExternalEventsOnly() {
    return Collections.singletonList(createExternalEventAllGroupsSourceEntry());
  }

  private EventCountResponseDto createNullGroupCountDto(final boolean suggested) {
    return toEventCountDto(nullGroupEvent, 2L, suggested);
  }

  private EventCountResponseDto createFrontendMayoCountDto(final boolean suggested) {
    return toEventCountDto(frontendMayoEvent, 2L, suggested);
  }

  private EventCountResponseDto createBackendMayoCountDto(final boolean suggested) {
    return toEventCountDto(backendMayoEvent, 3L, suggested);
  }

  private EventCountResponseDto createKetchupMayoCountDto(final boolean suggested) {
    return toEventCountDto(ketchupMayoEvent, 2L, suggested);
  }

  private EventCountResponseDto createManagementBbqCountDto(final boolean suggested) {
    return toEventCountDto(managementBbqEvent, 1L, suggested);
  }

  private EventCountResponseDto createBackendKetchupCountDto(final boolean suggested) {
    return toEventCountDto(backendKetchupEvent, 4L, suggested);
  }

  private EventCountResponseDto toEventCountDto(CloudEventRequestDto event, Long count, boolean suggested) {
    return EventCountResponseDto.builder()
      .group(event.getGroup().orElse(null))
      .source(event.getSource())
      .eventName(event.getType())
      .count(count)
      .suggested(suggested)
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
}
