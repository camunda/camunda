/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.eventprocess;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.util.IdGenerator;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class EventDeleteRestServiceRolloverIT extends AbstractEventRestServiceRolloverIT {

  @Test
  public void deleteRolledOverEvents_deleteSingleEventNotUsedInEventInstance() {
    // given an event for each index
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);
    final List<EventDto> savedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = Collections.singletonList(savedEventsBeforeDelete.get(0).getId());

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThatEventsHaveBeenDeleted(savedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  public void deleteRolledOverEvents_deleteSingleEventDoesNotExist() {
    // given an event for each index
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);
    final List<CloudEventRequestDto> instanceEvents = Arrays.asList(impostorSabotageNav, impostorMurderedMedBay);
    createAndSaveEventInstanceContainingEvents(instanceEvents, "indexId");
    final List<EventDto> savedEventsBeforeDelete = getAllStoredEvents();

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(Collections.singletonList("eventDoesNotExist"))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getAllStoredEvents()).containsExactlyInAnyOrderElementsOf(savedEventsBeforeDelete);
  }

  @Test
  public void deleteRolledOverEvents_deleteSingleEventUsedInSingleEventInstance() {
    // given
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);
    final List<CloudEventRequestDto> instanceEvents = Arrays.asList(impostorSabotageNav, impostorMurderedMedBay);
    final ProcessInstanceDto instance = createAndSaveEventInstanceContainingEvents(instanceEvents, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = Collections.singletonList(instanceEvents.get(0).getId());
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(instance.getProcessInstanceId()), eventIdsToDelete);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertEventInstancesDoNotContainAnyEventsOfIds(getAllStoredEventInstances(), eventIdsToDelete);
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  public void deleteRolledOverEvents_deleteMultipleEventsUsedInEventInstance() {
    // given
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);
    final List<CloudEventRequestDto> instanceEvents = Arrays.asList(impostorSabotageNav, impostorMurderedMedBay);
    final ProcessInstanceDto instance = createAndSaveEventInstanceContainingEvents(instanceEvents, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = instanceEvents.stream()
      .map(CloudEventRequestDto::getId)
      .collect(Collectors.toList());
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(instance.getProcessInstanceId()), eventIdsToDelete);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertEventInstancesDoNotContainAnyEventsOfIds(getAllStoredEventInstances(), eventIdsToDelete);
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  public void deleteRolledOverEvents_deleteSingleEventUsedInMultipleEventInstances() {
    // given
    ingestEventAndRolloverIndex(impostorSabotageNav);
    ingestEventAndRolloverIndex(impostorMurderedMedBay);
    ingestEventAndRolloverIndex(normieTaskNav);
    final List<CloudEventRequestDto> instanceEvents = Arrays.asList(impostorSabotageNav, impostorMurderedMedBay);
    final ProcessInstanceDto firstInstance = createAndSaveEventInstanceContainingEvents(instanceEvents, "indexId");
    final ProcessInstanceDto secondInstance = createAndSaveEventInstanceContainingEvents(instanceEvents, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = Collections.singletonList(instanceEvents.get(0).getId());
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(firstInstance.getProcessInstanceId()), eventIdsToDelete);
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(secondInstance.getProcessInstanceId()), eventIdsToDelete);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertEventInstancesDoNotContainAnyEventsOfIds(getAllStoredEventInstances(), eventIdsToDelete);
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  private void assertThatEventsHaveBeenDeleted(final List<EventDto> allSavedEventsBeforeDelete,
                                               final List<String> expectedDeletedEvenIds) {
    assertThat(getAllStoredEvents())
      .hasSize(allSavedEventsBeforeDelete.size() - expectedDeletedEvenIds.size())
      .extracting(EventDto::getId)
      .doesNotContainAnyElementsOf(expectedDeletedEvenIds);
  }

  private void assertEventInstanceContainsAllEventsOfIds(final EventProcessInstanceDto eventInstance,
                                                         final List<String> eventIds) {
    assertThat(eventInstance)
      .satisfies(storedInstance -> {
        assertThat(storedInstance.getEvents()).extracting(FlowNodeInstanceDto::getId).containsAll(eventIds);
      });
  }

  private void assertEventInstancesDoNotContainAnyEventsOfIds(final List<EventProcessInstanceDto> eventInstances,
                                                              final List<String> eventIds) {
    assertThat(eventInstances)
      .allSatisfy(storedInstance -> {
        assertThat(storedInstance.getEvents())
          .extracting(FlowNodeInstanceDto::getId)
          .doesNotContainAnyElementsOf(eventIds);
      });
  }

}
