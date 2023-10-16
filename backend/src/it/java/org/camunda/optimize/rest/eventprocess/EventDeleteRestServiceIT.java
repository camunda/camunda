/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import jakarta.ws.rs.core.Response;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventRequestDto;
import org.camunda.optimize.service.db.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventIndexES;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static jakarta.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;

public class EventDeleteRestServiceIT extends AbstractEventRestServiceIT {

  @Test
  void deleteEvents_userNotAuthenticated() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(Collections.singletonList(backendKetchupEvent.getId()))
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  void deleteEvents_userNotAuthorized() {
    // given
    removeAllUserEventProcessAuthorizations();

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(Collections.singletonList(backendKetchupEvent.getId()))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  void deleteEvents_emptyListOfIdsToDelete() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(Collections.emptyList())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  void deleteEvents_listOfIdsToDeleteLargerThanMaxAllowed() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(Collections.nCopies(1001, "someEventId"))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  void deleteEvents_deleteEventWithGroupAuthorization() {
    // given
    removeAllUserEventProcessAuthorizations();
    final String authorizedGroup = "humans";
    authorizationClient.createGroupAndAddUser(authorizedGroup, DEFAULT_USERNAME);
    embeddedOptimizeExtension.getConfigurationService()
      .getEventBasedProcessConfiguration()
      .getAuthorizedGroupIds().add(authorizedGroup);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(Collections.singletonList("someEventId"))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  void deleteEvents_deleteSingleEventNotUsedInEventInstance() {
    // given
    final List<EventDto> savedEvents = getAllStoredEvents();
    final List<String> eventIdsToDelete = Collections.singletonList(eventTraceOne.get(0).getId());

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThatEventsHaveBeenDeleted(savedEvents, eventIdsToDelete);
  }

  @Test
  void deleteEvents_deleteWholeEventTraceNotUsedInEventInstance() {
    // given
    final List<EventDto> savedEvents = getAllStoredEvents();
    final List<String> eventIdsToDelete = eventTraceOne.stream()
      .map(CloudEventRequestDto::getId)
      .collect(Collectors.toList());

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThatEventsHaveBeenDeleted(savedEvents, eventIdsToDelete);
  }

  @Test
  void deleteEvents_deleteSingleEventIdNotExistDoesNotFail() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(Collections.singletonList("idDoesNotExist"))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  void deleteEvents_deleteMultipleEventsOneIdNotExistDoesNotFail() {
    // given
    final List<EventDto> savedEvents = getAllStoredEvents();
    final List<String> realEventsToDelete = eventTraceOne.stream()
      .map(CloudEventRequestDto::getId)
      .collect(Collectors.toList());
    final ArrayList<String> eventsToRequestDelete = new ArrayList<>(realEventsToDelete);
    eventsToRequestDelete.add("thisEventIdDoesNotExist");

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventsToRequestDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThatEventsHaveBeenDeleted(savedEvents, realEventsToDelete);
  }

  @Test
  void deleteEvents_deleteSingleEventThatIsUsedInSingleEventInstance() {
    // given
    final ProcessInstanceDto instance = createAndSaveEventInstanceContainingEvents(eventTraceOne, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete =
      getAllStoredEvents();
    final List<String> eventIdsToDelete = Collections.singletonList(eventTraceOne.get(0).getId());
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
  void deleteEvents_deleteSingleEventThatIsUsedInSingleEventInstance_otherInstancesUnaffected() {
    // given
    final ProcessInstanceDto traceOneInst = createAndSaveEventInstanceContainingEvents(eventTraceOne, "indexId");
    final ProcessInstanceDto traceTwoInst = createAndSaveEventInstanceContainingEvents(eventTraceTwo, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = Collections.singletonList(eventTraceOne.get(0).getId());
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(traceOneInst.getProcessInstanceId()), eventIdsToDelete);
    assertEventInstancesDoNotContainAnyEventsOfIds(
      Collections.singletonList(getSavedInstanceWithId(traceTwoInst.getProcessInstanceId())), eventIdsToDelete);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getSavedInstanceWithId(traceTwoInst.getProcessInstanceId()).getFlowNodeInstances())
      .containsExactlyInAnyOrderElementsOf(traceTwoInst.getFlowNodeInstances());
    final List<String> expectedEventIdsRemaining = eventTraceOne.stream()
      .map(CloudEventRequestDto::getId)
      .filter(event -> !eventIdsToDelete.contains(event))
      .collect(Collectors.toList());
    assertThat(getSavedInstanceWithId(traceOneInst.getProcessInstanceId()).getFlowNodeInstances())
      .extracting(FlowNodeInstanceDto::getFlowNodeInstanceId)
      .containsExactlyInAnyOrderElementsOf(expectedEventIdsRemaining);
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  void deleteEvents_deleteSingleEventThatIsUsedInMultipleEventProcessInstances() {
    // given
    final ProcessInstanceDto traceOneInst = createAndSaveEventInstanceContainingEvents(eventTraceOne, "firstProcess");
    final ProcessInstanceDto traceTwoInst = createAndSaveEventInstanceContainingEvents(eventTraceOne, "secondProcess");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = Collections.singletonList(eventTraceOne.get(0).getId());
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(traceOneInst.getProcessInstanceId()), eventIdsToDelete);
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(traceTwoInst.getProcessInstanceId()), eventIdsToDelete);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    final List<String> expectedEventIdsRemaining = eventTraceOne.stream()
      .map(CloudEventRequestDto::getId)
      .filter(event -> !eventIdsToDelete.contains(event))
      .collect(Collectors.toList());
    assertThat(getAllStoredEventInstances())
      .extracting(EventProcessInstanceDto::getFlowNodeInstances)
      .allSatisfy(events -> assertThat(events)
        .extracting(FlowNodeInstanceDto::getFlowNodeInstanceId)
        .containsExactlyInAnyOrderElementsOf(expectedEventIdsRemaining));
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  void deleteEvents_deleteMultipleEventsThatAreUsedInSingleEventInstance() {
    // given
    final ProcessInstanceDto instance = createAndSaveEventInstanceContainingEvents(eventTraceOne, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = eventTraceOne.stream()
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
  void deleteEvents_deleteMultipleEventsThatAreUsedInSingleEventInstance_otherInstanceUnaffected() {
    // given
    final ProcessInstanceDto traceOneInst = createAndSaveEventInstanceContainingEvents(eventTraceOne, "indexId");
    final ProcessInstanceDto traceTwoInst = createAndSaveEventInstanceContainingEvents(eventTraceTwo, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = eventTraceOne.stream()
      .map(CloudEventRequestDto::getId)
      .collect(Collectors.toList());
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(traceOneInst.getProcessInstanceId()), eventIdsToDelete);
    assertEventInstancesDoNotContainAnyEventsOfIds(
      Collections.singletonList(getSavedInstanceWithId(traceTwoInst.getProcessInstanceId())), eventIdsToDelete);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getSavedInstanceWithId(traceTwoInst.getProcessInstanceId()).getFlowNodeInstances())
      .containsExactlyInAnyOrderElementsOf(traceTwoInst.getFlowNodeInstances());
    final List<String> expectedEventIdsRemaining = eventTraceOne.stream()
      .map(CloudEventRequestDto::getId)
      .filter(event -> !eventIdsToDelete.contains(event))
      .collect(Collectors.toList());
    assertThat(getSavedInstanceWithId(traceOneInst.getProcessInstanceId()).getFlowNodeInstances())
      .extracting(FlowNodeInstanceDto::getFlowNodeInstanceId)
      .containsExactlyInAnyOrderElementsOf(expectedEventIdsRemaining);
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  void deleteEvents_deleteMultipleEventsThatAreUsedInMultipleEventProcessInstances() {
    // given
    final ProcessInstanceDto traceOneInst = createAndSaveEventInstanceContainingEvents(eventTraceOne, "firstProcess");
    final ProcessInstanceDto traceTwoInst = createAndSaveEventInstanceContainingEvents(eventTraceOne, "secondProcess");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = eventTraceOne.stream()
      .map(CloudEventRequestDto::getId)
      .collect(Collectors.toList());
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(traceOneInst.getProcessInstanceId()), eventIdsToDelete);
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(traceTwoInst.getProcessInstanceId()), eventIdsToDelete);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    final List<String> expectedEventIdsRemaining = eventTraceOne.stream()
      .map(CloudEventRequestDto::getId)
      .filter(event -> !eventIdsToDelete.contains(event))
      .collect(Collectors.toList());
    assertThat(getAllStoredEventInstances())
      .extracting(EventProcessInstanceDto::getFlowNodeInstances)
      .allSatisfy(events -> assertThat(events)
        .extracting(FlowNodeInstanceDto::getFlowNodeInstanceId)
        .containsExactlyInAnyOrderElementsOf(expectedEventIdsRemaining));
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  void deleteEvents_deleteSingleEventThatIsUsedInSingleEventInstance_idempotentSecondDelete() {
    // given
    final ProcessInstanceDto instance = createAndSaveEventInstanceContainingEvents(eventTraceOne, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = Collections.singletonList(eventTraceOne.get(0).getId());
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

    // when the delete request is made again
    final Response secondDeleteResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then the response and instance state is the same
    assertThat(secondDeleteResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertEventInstancesDoNotContainAnyEventsOfIds(getAllStoredEventInstances(), eventIdsToDelete);
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  void deleteEvents_deleteMultipleEventsThatAreUsedInSingleEventInstance_idempotentSecondDelete() {
    // given
    final ProcessInstanceDto instance = createAndSaveEventInstanceContainingEvents(eventTraceOne, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = eventTraceOne.stream()
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

    // when the delete request is made again
    final Response secondDeleteResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then the response and instance state is the same
    assertThat(secondDeleteResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertEventInstancesDoNotContainAnyEventsOfIds(getAllStoredEventInstances(), eventIdsToDelete);
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  void deleteEvents_deleteSingleEventThatIsUsedInSingleEventInstance_firstInstanceEventDeleteFailsSecondRequestSucceeds() {
    // given
    final ProcessInstanceDto instance = createAndSaveEventInstanceContainingEvents(eventTraceOne, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = Collections.singletonList(eventTraceOne.get(0).getId());
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(instance.getProcessInstanceId()), eventIdsToDelete);
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*" + EventProcessInstanceIndex.constructIndexName("*") + ".*/_update_by_query")
      .withMethod(POST);
    esMockServer.when(requestMatcher, Times.once())
      .error(error().withDropConnection(true));

    // when a delete request will fail to delete the instance events
    embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then the instance and unsaved events are unchanged
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(getSavedInstanceWithId(instance.getProcessInstanceId()).getFlowNodeInstances())
      .containsExactlyInAnyOrderElementsOf(instance.getFlowNodeInstances());

    // when a second delete request is made
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then the event data is successfully removed
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertEventInstancesDoNotContainAnyEventsOfIds(getAllStoredEventInstances(), eventIdsToDelete);
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  void deleteEvents_deleteMultipleEventsThatAreUsedInSingleEventInstance_firstInstanceEventDeleteFailsSecondRequestSucceeds() {
    // given
    final ProcessInstanceDto instance = createAndSaveEventInstanceContainingEvents(eventTraceOne, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = eventTraceOne.stream()
      .map(CloudEventRequestDto::getId)
      .collect(Collectors.toList());
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(instance.getProcessInstanceId()), eventIdsToDelete);
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*" + EventProcessInstanceIndex.constructIndexName("*") + ".*/_update_by_query")
      .withMethod(POST);
    esMockServer.when(requestMatcher, Times.once())
      .error(error().withDropConnection(true));

    // when a delete request will fail to delete the instance events
    embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then the instance and unsaved events are unchanged
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(getSavedInstanceWithId(instance.getProcessInstanceId()).getFlowNodeInstances())
      .containsExactlyInAnyOrderElementsOf(instance.getFlowNodeInstances());

    // when a second delete request is made
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then the event data is successfully removed
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertEventInstancesDoNotContainAnyEventsOfIds(getAllStoredEventInstances(), eventIdsToDelete);
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  void deleteEvents_deleteSingleEventThatIsUsedInSingleEventInstance_firstEventDeletionFails() {
    // given
    final ProcessInstanceDto instance = createAndSaveEventInstanceContainingEvents(eventTraceOne, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = Collections.singletonList(eventTraceOne.get(0).getId());
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(instance.getProcessInstanceId()), eventIdsToDelete);
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/" + embeddedOptimizeExtension.getIndexNameService()
        // TODO don't use ES coupling here, to be dealt with OPT-7246
        .getOptimizeIndexNameWithVersionWithoutSuffix(new EventIndexES()) + ".*/_delete_by_query")
      .withMethod(POST);
    esMockServer.when(requestMatcher, Times.once())
      .error(error().withDropConnection(true));

    // when a delete request will fail to delete the event
    embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then the instance has already had the event removed
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertEventInstancesDoNotContainAnyEventsOfIds(getAllStoredEventInstances(), eventIdsToDelete);
    assertThat(getAllStoredEvents()).containsExactlyInAnyOrderElementsOf(allSavedEventsBeforeDelete);

    // when a second delete request is made
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then the event is successfully deleted
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertEventInstancesDoNotContainAnyEventsOfIds(getAllStoredEventInstances(), eventIdsToDelete);
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  @Test
  void deleteEvents_deleteMultipleEventsThatAreUsedInSingleEventInstance_firstEventDeletionFails() {
    // given
    final ProcessInstanceDto instance = createAndSaveEventInstanceContainingEvents(eventTraceOne, "indexId");
    final List<EventDto> allSavedEventsBeforeDelete = getAllStoredEvents();
    final List<String> eventIdsToDelete = eventTraceOne.stream()
      .map(CloudEventRequestDto::getId)
      .collect(Collectors.toList());
    assertEventInstanceContainsAllEventsOfIds(
      getSavedInstanceWithId(instance.getProcessInstanceId()), eventIdsToDelete);
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/" + embeddedOptimizeExtension.getIndexNameService()
        // TODO don't use ES coupling here, to be dealt with OPT-7246
        .getOptimizeIndexNameWithVersionWithoutSuffix(new EventIndexES()) + ".*/_delete_by_query")
      .withMethod(POST);
    esMockServer.when(requestMatcher, Times.once())
      .error(error().withDropConnection(true));

    // when a delete request will fail to delete the events
    embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then the instance has already had the events removed
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertEventInstancesDoNotContainAnyEventsOfIds(getAllStoredEventInstances(), eventIdsToDelete);
    assertThat(getAllStoredEvents()).containsExactlyInAnyOrderElementsOf(allSavedEventsBeforeDelete);

    // when a second delete request is made
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteEventsRequest(eventIdsToDelete)
      .execute();

    // then the events are successfully deleted
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertEventInstancesDoNotContainAnyEventsOfIds(getAllStoredEventInstances(), eventIdsToDelete);
    assertThatEventsHaveBeenDeleted(allSavedEventsBeforeDelete, eventIdsToDelete);
  }

  private void assertThatEventsHaveBeenDeleted(final List<EventDto> allSavedEventsBeforeDelete,
                                               final List<String> expectedDeletedEventIds) {
    assertThat(getAllStoredEvents())
      .hasSize(allSavedEventsBeforeDelete.size() - expectedDeletedEventIds.size())
      .extracting(EventDto::getId)
      .doesNotContainAnyElementsOf(expectedDeletedEventIds);
  }

  private void assertEventInstanceContainsAllEventsOfIds(final EventProcessInstanceDto eventInstance,
                                                         final List<String> eventIds) {
    assertThat(eventInstance)
      .satisfies(storedInstance -> {
        assertThat(storedInstance.getFlowNodeInstances()).extracting(FlowNodeInstanceDto::getFlowNodeInstanceId).containsAll(eventIds);
      });
  }

  private void assertEventInstancesDoNotContainAnyEventsOfIds(final List<EventProcessInstanceDto> eventInstances,
                                                              final List<String> eventIds) {
    assertThat(eventInstances)
      .singleElement()
      .satisfies(storedInstance -> {
        assertThat(storedInstance.getFlowNodeInstances())
          .extracting(FlowNodeInstanceDto::getFlowNodeInstanceId)
          .doesNotContainAnyElementsOf(eventIds);
      });
  }

}
