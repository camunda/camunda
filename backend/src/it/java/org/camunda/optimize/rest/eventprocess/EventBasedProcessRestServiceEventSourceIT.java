/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceConfigDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.ExternalEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventAllGroupsSourceEntry;
import static org.camunda.optimize.test.optimize.EventProcessClient.createExternalEventSourceEntryForGroup;
import static org.camunda.optimize.test.optimize.EventProcessClient.createSimpleCamundaEventSourceEntry;

public class EventBasedProcessRestServiceEventSourceIT extends AbstractEventProcessIT {

  private final String PROCESS_DEF_KEY_1 = "aProcessDefinitionKey_1";
  private final String PROCESS_DEF_KEY_2 = "aProcessDefinitionKey_2";

  @Test
  public void createWithNoEventSource() {
    // given
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(null);
    grantAuthorizationsToDefaultUser(PROCESS_DEF_KEY_1);

    // when
    Response response = eventProcessClient
      .createCreateEventProcessMappingRequest(eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createWithCamundaEventSource() {
    // given
    final CamundaEventSourceEntryDto camundaEventSourceEntryDto = createCamundaSourceEntryForImportedDefinition(
      PROCESS_DEF_KEY_1);
    final EventProcessMappingDto eventProcessMapping =
      createWithEventSourceEntries(Collections.singletonList(camundaEventSourceEntryDto));

    // when
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // then
    List<EventSourceEntryDto<?>> eventSources =
      eventProcessClient.getEventProcessMapping(eventProcessMappingId).getEventSources();
    assertEventSourcesContainsExpectedEntries(
      eventSources, Collections.singletonList(camundaEventSourceEntryDto));
  }

  @Test
  public void createWithCamundaAndExternalEventSource() {
    // given
    List<EventSourceEntryDto<?>> eventSourceEntryDtos = new ArrayList<>();
    final ExternalEventSourceEntryDto externalEventSourceEntry = createExternalEventAllGroupsSourceEntry();
    final CamundaEventSourceEntryDto camundaEventSourceEntry =
      createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    eventSourceEntryDtos.add(externalEventSourceEntry);
    eventSourceEntryDtos.add(camundaEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    // when
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // then
    List<EventSourceEntryDto<?>> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();
    assertEventSourcesContainsExpectedEntries(
      eventSources, Arrays.asList(camundaEventSourceEntry, externalEventSourceEntry));
  }

  @Test
  public void createWithExternalEventSource() {
    // given
    final ExternalEventSourceEntryDto externalEventSourceEntry = createExternalEventAllGroupsSourceEntry();
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Collections.singletonList(
      externalEventSourceEntry));

    // when
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // then
    List<EventSourceEntryDto<?>> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();
    assertEventSourcesContainsExpectedEntries(
      eventSources, Collections.singletonList(externalEventSourceEntry));
  }

  @Test
  public void createWithDuplicateCamundaEventSource_fails() {
    // given
    final CamundaEventSourceEntryDto eventSourceEntry =
      createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Arrays.asList(
      eventSourceEntry,
      eventSourceEntry
    ));

    // when
    ConflictResponseDto conflictResponseDto = eventProcessClient
      .createCreateEventProcessMappingRequest(eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeConflictException.ERROR_CODE);
  }

  @Test
  public void createWithMultipleExternalEventSources() {
    // given
    final List<EventSourceEntryDto<?>> eventSources = Arrays.asList(
      createExternalEventSourceEntryForGroup("groupA"),
      createExternalEventSourceEntryForGroup("groupB")
    );
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSources);

    // when
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // then
    List<EventSourceEntryDto<?>> savedSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();
    assertEventSourcesContainsExpectedEntries(savedSources, eventSources);
  }

  @ParameterizedTest
  @MethodSource("invalidExternalEventSourceCombinations")
  public void createEventProcessMapping_invalidExternalEventSourceCombination(final List<EventSourceEntryDto<?>> sources) {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
        Collections.emptyMap(), "process name", createTwoEventAndOneTaskActivitiesProcessDefinitionXml(), sources
      );

    // when
    final Response response =
      eventProcessClient.createCreateEventProcessMappingRequest(eventProcessMappingDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("invalidExternalEventSourceCombinations")
  public void updateEventProcessMapping_invalidExternalEventSourceCombination(final List<EventSourceEntryDto<?>> sources) {
    // given
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.buildEventProcessMappingDto(createTwoEventAndOneTaskActivitiesProcessDefinitionXml())
    );
    EventProcessMappingDto updateDto = eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      Collections.emptyMap(),
      "new process name",
      createTwoEventAndOneTaskActivitiesProcessDefinitionXml()
    );
    updateDto.setEventSources(sources);

    // when
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(storedEventProcessMappingId, updateDto).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createWithEventProcessEventSources_fails() {
    // given
    addEventProcessDefinitionDtoToElasticsearch();
    final CamundaEventSourceEntryDto eventSourceEntry = createSimpleCamundaEventSourceEntry(
      PROCESS_DEF_KEY_1
    );
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Arrays.asList(
      eventSourceEntry
    ));

    // when
    ConflictResponseDto conflictResponseDto = eventProcessClient
      .createCreateEventProcessMappingRequest(eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeConflictException.ERROR_CODE);
  }

  @Test
  public void updateEventSource() {
    // given
    List<EventSourceEntryDto<?>> eventSourceEntryDtos = new ArrayList<>();
    final ExternalEventSourceEntryDto externalEventSourceEntry = createExternalEventAllGroupsSourceEntry();
    eventSourceEntryDtos.add(externalEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    final CamundaEventSourceEntryDto camundaEventSourceEntry =
      createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    eventProcessMapping.getEventSources().add(camundaEventSourceEntry);

    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    camundaEventSourceEntry.getConfiguration()
      .setEventScope(Collections.singletonList(EventScopeType.PROCESS_INSTANCE));
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);

    List<EventSourceEntryDto<?>> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    // then
    assertEventSourcesContainsExpectedEntries(
      eventSources,
      Arrays.asList(camundaEventSourceEntry, externalEventSourceEntry)
    );
  }

  @Test
  public void updateEventSource_multipleEventScopes() {
    // given
    CamundaEventSourceEntryDto camundaEventSourceEntryDto =
      createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(Collections.singletonList(
      camundaEventSourceEntryDto));
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // then
    List<EventSourceEntryDto<?>> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();
    assertEventSourcesContainsExpectedEntries(
      eventSources, Collections.singletonList(camundaEventSourceEntryDto));

    // when
    camundaEventSourceEntryDto.getConfiguration()
      .setEventScope(Arrays.asList(EventScopeType.START_END, EventScopeType.PROCESS_INSTANCE));
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);

    // then
    List<EventSourceEntryDto<?>> updatedEventSources = eventProcessClient.getEventProcessMapping(
      eventProcessMappingId)
      .getEventSources();
    assertEventSourcesContainsExpectedEntries(
      updatedEventSources, Collections.singletonList(camundaEventSourceEntryDto));
  }

  @Test
  public void updateWithNoEventSource() {
    // given
    List<EventSourceEntryDto<?>> camundaEventSourceEntryDtos = new ArrayList<>();
    camundaEventSourceEntryDtos.add(createExternalEventAllGroupsSourceEntry());
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(camundaEventSourceEntryDtos);

    // when
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);
    eventProcessMapping.setEventSources(null);
    Response response = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMapping)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateWithDuplicateCamundaEventSource_fails() {
    // given
    final CamundaEventSourceEntryDto camundaEventSourceEntry =
      createCamundaSourceEntryForImportedDefinition(PROCESS_DEF_KEY_1);
    List<EventSourceEntryDto<?>> eventSourceEntryDtos = new ArrayList<>();
    eventSourceEntryDtos.add(camundaEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    eventProcessMapping.getEventSources().add(camundaEventSourceEntry);
    ConflictResponseDto conflictResponseDto = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMapping)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    List<EventSourceEntryDto<?>> eventSources = eventProcessClient.getEventProcessMapping(eventProcessMappingId)
      .getEventSources();

    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeConflictException.ERROR_CODE);
    assertEventSourcesContainsExpectedEntries(eventSources, Collections.singletonList(camundaEventSourceEntry));
  }

  @Test
  public void updateWithMultipleExternalEventSources() {
    // given
    List<EventSourceEntryDto<?>> eventSourceEntryDtos = new ArrayList<>();
    final ExternalEventSourceEntryDto groupA = createExternalEventSourceEntryForGroup("aGroup");
    final ExternalEventSourceEntryDto groupB = createExternalEventSourceEntryForGroup("bGroup");
    eventSourceEntryDtos.add(groupA);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    eventProcessMapping.getEventSources().add(groupB);
    eventProcessClient.updateEventProcessMapping(eventProcessMappingId, eventProcessMapping);
    List<EventSourceEntryDto<?>> updatedEventSources =
      eventProcessClient.getEventProcessMapping(eventProcessMappingId).getEventSources();

    // then
    assertEventSourcesContainsExpectedEntries(
      updatedEventSources, Arrays.asList(groupA, groupB));
  }

  @Test
  public void updateWithEventProcessEventSources_fails() {
    // given
    addEventProcessDefinitionDtoToElasticsearch();
    final CamundaEventSourceEntryDto eventProcessEventSource = createSimpleCamundaEventSourceEntry(
      PROCESS_DEF_KEY_1
    );
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(new ArrayList<>());

    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    eventProcessMapping.getEventSources().add(eventProcessEventSource);
    ConflictResponseDto conflictResponseDto = eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMapping)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeConflictException.ERROR_CODE);
  }

  @Test
  public void deleteEventSource() {
    // given
    List<EventSourceEntryDto<?>> eventSourceEntryDtos = new ArrayList<>();
    final ExternalEventSourceEntryDto externalEventSourceEntry = createExternalEventAllGroupsSourceEntry();
    eventSourceEntryDtos.add(externalEventSourceEntry);
    final EventProcessMappingDto eventProcessMapping = createWithEventSourceEntries(eventSourceEntryDtos);

    final CamundaEventSourceEntryDto camundaSourceEntry1 = createCamundaSourceEntryForImportedDefinition(
      PROCESS_DEF_KEY_1);
    final CamundaEventSourceEntryDto camundaSourceEntry2 = createCamundaSourceEntryForImportedDefinition(
      PROCESS_DEF_KEY_2);
    eventProcessMapping.getEventSources().add(camundaSourceEntry1);
    eventProcessMapping.getEventSources().add(camundaSourceEntry2);
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(eventProcessMapping);

    // when
    eventProcessMapping.getEventSources().remove(camundaSourceEntry1);
    performUpdateMappingRequest(eventProcessMappingId, eventProcessMapping);

    // then
    List<EventSourceEntryDto<?>> eventSources =
      eventProcessClient.getEventProcessMapping(eventProcessMappingId).getEventSources();
    assertEventSourcesContainsExpectedEntries(
      eventSources, Arrays.asList(camundaSourceEntry2, externalEventSourceEntry));
  }

  private void addEventProcessDefinitionDtoToElasticsearch() {
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      PROCESS_DEF_KEY_1,
      PROCESS_DEF_KEY_1,
      "1",
      Collections.singletonList(new IdentityDto(DEFAULT_USERNAME, IdentityType.USER))
    );
  }

  private EventProcessMappingDto createWithEventSourceEntries(final List<EventSourceEntryDto<?>> eventSourceEntries) {
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsWithXmlAndEventSources(
      null,
      null,
      createTwoEventAndOneTaskActivitiesProcessDefinitionXml(),
      eventSourceEntries
    );
  }

  private void performUpdateMappingRequest(final String eventProcessMappingId,
                                           final EventProcessMappingDto eventProcessMappingDto) {
    eventProcessClient
      .createUpdateEventProcessMappingRequest(eventProcessMappingId, eventProcessMappingDto)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  private void assertEventSourcesContainsExpectedEntries(List<EventSourceEntryDto<?>> actualEventSources,
                                                         List<EventSourceEntryDto<?>> expectedEntries) {
    assertThat(actualEventSources)
      .hasSameSizeAs(expectedEntries)
      .allSatisfy(source -> assertThat(source.getId()).isNotNull())
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
        EventSourceEntryDto.Fields.id,
        EventSourceEntryDto.Fields.configuration
      )
      .containsExactlyInAnyOrderElementsOf(expectedEntries);
    final Map<String, EventSourceEntryDto<?>> sourcesByIdentifier = actualEventSources.stream()
      .collect(Collectors.toMap(EventSourceEntryDto::getSourceIdentifier, Function.identity()));
    expectedEntries.forEach(expectedEntry -> {
      final EventSourceEntryDto<?> actualEntryForIdentifier =
        sourcesByIdentifier.get(expectedEntry.getSourceIdentifier());
      assertThat(actualEntryForIdentifier).isNotNull();
      if (expectedEntry instanceof CamundaEventSourceEntryDto) {
        assertThat((CamundaEventSourceConfigDto) actualEntryForIdentifier.getConfiguration())
          .usingRecursiveComparison()
          .ignoringFields(CamundaEventSourceConfigDto.Fields.processDefinitionName)
          .isEqualTo(expectedEntry.getConfiguration());
      } else if (expectedEntry instanceof ExternalEventSourceEntryDto) {
        assertThat((ExternalEventSourceConfigDto) actualEntryForIdentifier.getConfiguration())
          .usingRecursiveComparison()
          .isEqualTo(expectedEntry.getConfiguration());
      }
    });
  }

}
