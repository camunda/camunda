/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.LabelDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.service.EventProcessService;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.rest.eventprocess.EventBasedProcessRestServiceIT.createProcessDefinitionXml;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.EventProcessClient.createEventMappingsDto;
import static org.camunda.optimize.test.optimize.EventProcessClient.createMappedEventDto;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_LABEL_INDEX_NAME;
import static org.mockserver.model.HttpRequest.request;

public class EventBasedProcessDeleteIT extends AbstractEventProcessIT {

  private static String simpleDiagramXml;

  final LabelDto FIRST_LABEL = new LabelDto("first label", "a name", VariableType.STRING);
  final LabelDto SECOND_LABEL = new LabelDto("second label", "a name", VariableType.STRING);

  @RegisterExtension
  @Order(5)
  protected final LogCapturer logCapturer = LogCapturer.create().forLevel(Level.DEBUG)
    .captureForType(EventProcessService.class);

  @BeforeAll
  public static void setup() {
    simpleDiagramXml = createProcessDefinitionXml();
  }

  @Test
  public void deleteEventProcessMapping() {
    // given
    String storedEventProcessMappingId = eventProcessClient.createEventProcessMapping(
      eventProcessClient.buildEventProcessMappingDto(simpleDiagramXml)
    );

    // when
    Response response = eventProcessClient
      .createDeleteEventProcessMappingRequest(storedEventProcessMappingId).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertGetMappingRequestStatusCode(storedEventProcessMappingId, Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void deletePublishedEventProcessMapping() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessId = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    publishMappingAndExecuteImport(eventProcessId);
    final EventProcessPublishStateDto publishState = getEventProcessPublishStateDto(eventProcessId);
    assertThat(eventInstanceIndexForPublishStateExists(publishState)).isTrue();

    // when
    eventProcessClient.deleteEventProcessMapping(eventProcessId);
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().runImportRound();

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId)).isEmpty();
    assertThat(eventInstanceIndexForPublishStateExists(publishState)).isFalse();
  }

  @Test
  public void deletePublishedEventProcessMapping_dependentResourcesGetCleared() {
    // given a published event process with various dependent resources created using its definition
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);
    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto(
      STARTED_EVENT, FINISHED_EVENT
    );
    String eventProcessDefinitionKeyToDelete = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);
    String nonDeletedEventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);

    publishMappingAndExecuteImport(eventProcessDefinitionKeyToDelete);
    publishMappingAndExecuteImport(nonDeletedEventProcessDefinitionKey);
    EventProcessPublishStateDto publishState = getEventProcessPublishStateDto(eventProcessDefinitionKeyToDelete);
    assertThat(eventInstanceIndexForPublishStateExists(publishState)).isTrue();
    executeImportCycle();

    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(PROCESS, eventProcessDefinitionKeyToDelete)
    );

    addLabelForEventProcessDefinition(eventProcessDefinitionKeyToDelete, FIRST_LABEL);
    DefinitionVariableLabelsDto nonDeletedDefinitionVariableLabelsDto = addLabelForEventProcessDefinition(
      nonDeletedEventProcessDefinitionKey,
      SECOND_LABEL
    );

    String reportWithEventProcessDefKey = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(
        collectionId,
        eventProcessDefinitionKeyToDelete,
        Collections.emptyList()
      ));
    String reportIdWithDefaultDefKey = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(
        collectionId,
        DEFAULT_DEFINITION_KEY,
        Collections.emptyList()
      ));
    String reportIdWithNoDefKey = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(collectionId, Collections.emptyList()));
    reportClient.createCombinedReport(
      collectionId,
      Arrays.asList(reportWithEventProcessDefKey, reportIdWithDefaultDefKey)
    );

    alertClient.createAlertForReport(reportWithEventProcessDefKey);
    String alertIdToRemain = alertClient.createAlertForReport(reportIdWithDefaultDefKey);

    String dashboardId = dashboardClient.createDashboard(
      collectionId, Arrays.asList(reportWithEventProcessDefKey, reportIdWithDefaultDefKey, reportIdWithNoDefKey)
    );

    // when the event process is deleted
    eventProcessClient.deleteEventProcessMapping(eventProcessDefinitionKeyToDelete);
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().runImportRound();

    // then the event process is deleted and the associated resources are cleaned up
    assertGetMappingRequestStatusCode(eventProcessDefinitionKeyToDelete, Response.Status.NOT_FOUND.getStatusCode());
    assertThat(collectionClient.getReportsForCollection(collectionId))
      .extracting(AuthorizedReportDefinitionResponseDto.Fields.definitionDto + "." + ReportDefinitionDto.Fields.id)
      .containsExactlyInAnyOrder(reportIdWithDefaultDefKey, reportIdWithNoDefKey);
    assertThat(alertClient.getAlertsForCollectionAsDefaultUser(collectionId))
      .extracting(AlertDefinitionDto.Fields.id)
      .containsExactly(alertIdToRemain);
    assertThat(getAllCollectionDefinitions())
      .hasSize(1)
      .extracting(CollectionDefinitionDto.Fields.data + "." + CollectionDataDto.Fields.scope)
      .contains(Collections.singletonList(new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY)));
    assertThat(dashboardClient.getDashboard(dashboardId).getTiles())
      .extracting(DashboardReportTileDto.Fields.id)
      .containsExactlyInAnyOrder(reportIdWithDefaultDefKey, reportIdWithNoDefKey);
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessDefinitionKeyToDelete)).isEmpty();
    assertThat(eventInstanceIndexForPublishStateExists(publishState)).isFalse();
    assertThat(getAllDocumentsOfVariableLabelIndex()).hasSize(1).containsExactly(nonDeletedDefinitionVariableLabelsDto);
  }

  @Test
  public void deletePublishedEventProcessMapping_labelDocumentDoesNotExist() {
    // given
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);
    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto(
      STARTED_EVENT, FINISHED_EVENT
    );
    String eventProcessDefinitionKeyToDelete =
      eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);
    String nonDeletedEventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);

    publishMappingAndExecuteImport(nonDeletedEventProcessDefinitionKey);
    publishMappingAndExecuteImport(eventProcessDefinitionKeyToDelete);
    EventProcessPublishStateDto eventProcessDefinitionKeyToDeletePublishState = getEventProcessPublishStateDto(eventProcessDefinitionKeyToDelete);
    assertThat(eventInstanceIndexForPublishStateExists(eventProcessDefinitionKeyToDeletePublishState)).isTrue();
    EventProcessPublishStateDto nonDeletedEventProcessDefinitionPublishState = getEventProcessPublishStateDto(nonDeletedEventProcessDefinitionKey);
    assertThat(eventInstanceIndexForPublishStateExists(nonDeletedEventProcessDefinitionPublishState)).isTrue();
    executeImportCycle();

    addLabelForEventProcessDefinition(nonDeletedEventProcessDefinitionKey, FIRST_LABEL);
    DefinitionVariableLabelsDto nonDeletedDefinitionVariableLabelsDto = addLabelForEventProcessDefinition(
      nonDeletedEventProcessDefinitionKey,
      SECOND_LABEL
    );

    // when
    eventProcessClient.deleteEventProcessMapping(eventProcessDefinitionKeyToDelete);
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().runImportRound();

    // then
    assertGetMappingRequestStatusCode(eventProcessDefinitionKeyToDelete, Response.Status.NOT_FOUND.getStatusCode());
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessDefinitionKeyToDelete)).isEmpty();
    assertThat(eventInstanceIndexForPublishStateExists(eventProcessDefinitionKeyToDeletePublishState)).isFalse();
    assertThat(getAllDocumentsOfVariableLabelIndex()).hasSize(1).containsExactly(nonDeletedDefinitionVariableLabelsDto);
  }

  @Test
  public void eventProcessMappingNotDeleted_ifEsFailsToDeleteReportsUsingMapping() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey);
    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(PROCESS, eventProcessDefinitionKey)
    );
    String reportUsingMapping = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(
        collectionId,
        eventProcessDefinitionKey,
        Collections.emptyList()
      ));

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*" + SINGLE_PROCESS_REPORT_INDEX_NAME + ".*/_delete_by_query")
      .withMethod(POST);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    eventProcessClient.createDeleteEventProcessMappingRequest(eventProcessDefinitionKey)
      .execute(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey, Response.Status.OK.getStatusCode());
    assertThat(collectionClient.getReportsForCollection(collectionId))
      .extracting(AuthorizedReportDefinitionResponseDto.Fields.definitionDto + "." + ReportDefinitionDto.Fields.id)
      .containsExactlyInAnyOrder(reportUsingMapping);
  }

  @Test
  public void eventProcessMappingNotDeleted_ifEsFailsToDeleteMappingAsScopeEntry() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey);
    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(PROCESS, eventProcessDefinitionKey)
    );

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*" + COLLECTION_INDEX_NAME + ".*/_update_by_query")
      .withMethod(POST);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    eventProcessClient.createDeleteEventProcessMappingRequest(eventProcessDefinitionKey)
      .execute(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey, Response.Status.OK.getStatusCode());
    assertThat(collectionClient.getCollectionById(collectionId).getData().getScope())
      .extracting(CollectionScopeEntryDto::getDefinitionKey)
      .contains(eventProcessDefinitionKey);
  }

  @Test
  public void eventProcessMappingNotDeleted_ifEsFailsToDeletePublishState() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey);

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME + "/_update_by_query")
      .withMethod(POST);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    eventProcessClient.createDeleteEventProcessMappingRequest(eventProcessDefinitionKey)
      .execute(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey, Response.Status.OK.getStatusCode());
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessDefinitionKey)).isNotEmpty();
  }

  @Test
  public void deleteEventProcessMapping_notExists() {
    // when
    Response response = eventProcessClient
      .createDeleteEventProcessMappingRequest("doesNotExist")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void bulkDeleteEventProcessMappings_notPossibleForUnauthenticatedUser() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .bulkDeleteEventProcessMappingsRequest(Arrays.asList("someId", "someOtherId"))
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void bulkDeleteEventProcessMappings_forbiddenForUnauthorizedUser() {
    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .bulkDeleteEventProcessMappingsRequest(Arrays.asList("someId", "someOtherId"))
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void bulkDeleteEventProcessMappings_emptyEventBasedProcessIdList() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .bulkDeleteEventProcessMappingsRequest(Collections.emptyList())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void bulkDeleteEventProcessMappings_nullEventBasedProcessIdList() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .bulkDeleteEventProcessMappingsRequest(null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void bulkDeleteEventProcessMappings() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey1 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey2 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    List<String> eventProcessMapings = Arrays.asList(eventProcessDefinitionKey1, eventProcessDefinitionKey2);

    // when
    Response response = eventProcessClient
      .createBulkDeleteEventProcessMappingsRequest(eventProcessMapings).execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey1, Response.Status.NOT_FOUND.getStatusCode());
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey2, Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void bulkDeletePublishedEventProcessMappings() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessId1 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    publishMappingAndExecuteImport(eventProcessId1);
    final EventProcessPublishStateDto publishState1 = getEventProcessPublishStateDto(eventProcessId1);
    assertThat(eventInstanceIndexForPublishStateExists(publishState1)).isTrue();
    String eventProcessId2 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    publishMappingAndExecuteImport(eventProcessId2);
    final EventProcessPublishStateDto publishState2 = getEventProcessPublishStateDto(eventProcessId2);
    assertThat(eventInstanceIndexForPublishStateExists(publishState2)).isTrue();

    List<String> eventBasedProcesses = Arrays.asList(eventProcessId1, eventProcessId2);

    // when
    eventProcessClient.createBulkDeleteEventProcessMappingsRequest(eventBasedProcesses).execute();
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().runImportRound();

    // then
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId1)).isEmpty();
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessId2)).isEmpty();
    assertThat(eventInstanceIndexForPublishStateExists(publishState1)).isFalse();
    assertThat(eventInstanceIndexForPublishStateExists(publishState2)).isFalse();
    assertGetMappingRequestStatusCode(eventProcessId1, Response.Status.NOT_FOUND.getStatusCode());
    assertGetMappingRequestStatusCode(eventProcessId2, Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void eventProcessMappingsSkippedOnBulkDelete_ifEsFailsToDeleteReportsUsingMapping() {
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey1 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey2 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey1);
    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey2);
    String reportUsingMapping = reportClient.createAndStoreProcessReport(eventProcessDefinitionKey1);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*" + SINGLE_PROCESS_REPORT_INDEX_NAME + ".*/_delete_by_query")
      .withMethod(POST);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    List<String> eventProcessIds = Arrays.asList(
      eventProcessDefinitionKey1,
      eventProcessDefinitionKey2
    );

    // when
    Response response = eventProcessClient
      .createBulkDeleteEventProcessMappingsRequest(eventProcessIds)
      .execute();

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey1, Response.Status.OK.getStatusCode());
    assertThat(reportClient.getReportById(reportUsingMapping)).isNotNull();
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey2, Response.Status.NOT_FOUND.getStatusCode());
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessDefinitionKey1)).isNotEmpty();
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessDefinitionKey2)).isEmpty();
    logCapturer.assertContains(
      "There was an error while deleting resources associated to the event process mapping with id " + eventProcessDefinitionKey1);
  }

  @Test
  public void bulkDeleteEventProcessMappings_skipsDeletionWhenEventProcessMappingDoesNotExist() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey1 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey2 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    List<String> eventProcessIds = Arrays.asList(
      eventProcessDefinitionKey1,
      "doesNotExist1",
      eventProcessDefinitionKey2
    );
    Response response = eventProcessClient
      .createBulkDeleteEventProcessMappingsRequest(eventProcessIds)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey1, Response.Status.NOT_FOUND.getStatusCode());
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey2, Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void bulkDeleteEventProcessMappings_skipsDeletionIfEsFailsToDeletePublishState() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey1 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey2 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey1);

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME + "/_update_by_query")
      .withMethod(POST);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    List<String> eventProcessIds = Arrays.asList(eventProcessDefinitionKey1, eventProcessDefinitionKey2);

    // when
    Response response = eventProcessClient.createBulkDeleteEventProcessMappingsRequest(eventProcessIds).execute();

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.exactly(2));
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey1, Response.Status.OK.getStatusCode());
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessDefinitionKey1)).isNotEmpty();
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey2, Response.Status.NOT_FOUND.getStatusCode());
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessDefinitionKey2)).isEmpty();
    logCapturer.assertContains(
      "There was an error while deleting resources associated to the event process mapping with id " + eventProcessDefinitionKey1);
  }

  @Test
  public void bulkDeleteEventProcessMapping_skipsDeletionIfEsFailsToDeleteMappingAsScopeEntry() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey1 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey2 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey1);
    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(PROCESS, eventProcessDefinitionKey1)
    );

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*" + COLLECTION_INDEX_NAME + ".*/_update_by_query")
      .withMethod(POST);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    List<String> eventProcessIds = Arrays.asList(eventProcessDefinitionKey1, eventProcessDefinitionKey2);

    // when
    eventProcessClient.createBulkDeleteEventProcessMappingsRequest(eventProcessIds).execute();

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.exactly(2));
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey1, Response.Status.OK.getStatusCode());
    assertThat(collectionClient.getCollectionById(collectionId).getData().getScope())
      .extracting(CollectionScopeEntryDto::getDefinitionKey)
      .contains(eventProcessDefinitionKey1);
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessDefinitionKey1)).isNotEmpty();
    assertGetMappingRequestStatusCode(eventProcessDefinitionKey2, Response.Status.NOT_FOUND.getStatusCode());
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(eventProcessDefinitionKey2)).isEmpty();
    logCapturer.assertContains(
      "There was an error while deleting resources associated to the event process mapping with id " + eventProcessDefinitionKey1);

  }

  @Test
  public void bulkDeletePublishedEventProcessMappings_dependentResourcesGetCleared() {
    // given
    ingestTestEvent(STARTED_EVENT);
    ingestTestEvent(FINISHED_EVENT);
    final EventProcessMappingDto simpleEventProcessMappingDto = buildSimpleEventProcessMappingDto(
      STARTED_EVENT, FINISHED_EVENT
    );
    String firstDeletingEventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);
    String secondDeletingEventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);
    String nonDeletedEventProcessDefinitionKey = eventProcessClient.createEventProcessMapping(simpleEventProcessMappingDto);

    publishMappingAndExecuteImport(firstDeletingEventProcessDefinitionKey);
    publishMappingAndExecuteImport(secondDeletingEventProcessDefinitionKey);
    publishMappingAndExecuteImport(nonDeletedEventProcessDefinitionKey);
    EventProcessPublishStateDto publishState = getEventProcessPublishStateDto(firstDeletingEventProcessDefinitionKey);
    assertThat(eventInstanceIndexForPublishStateExists(publishState)).isTrue();
    executeImportCycle();

    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    addLabelForEventProcessDefinition(firstDeletingEventProcessDefinitionKey, FIRST_LABEL);
    DefinitionVariableLabelsDto nonDeletedDefinitionVariableLabelsDto = addLabelForEventProcessDefinition(
      nonDeletedEventProcessDefinitionKey,
      SECOND_LABEL
    );

    collectionClient.addScopeEntryToCollection(
      collectionId,
      new CollectionScopeEntryDto(PROCESS, firstDeletingEventProcessDefinitionKey)
    );

    String reportWithEventProcessDefKey = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(
        collectionId,
        firstDeletingEventProcessDefinitionKey,
        Collections.emptyList()
      ));
    String reportIdWithDefaultDefKey = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(
        collectionId,
        DEFAULT_DEFINITION_KEY,
        Collections.emptyList()
      ));
    String reportIdWithNoDefKey = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(collectionId, Collections.emptyList()));
    reportClient.createCombinedReport(
      collectionId,
      Arrays.asList(reportWithEventProcessDefKey, reportIdWithDefaultDefKey)
    );

    alertClient.createAlertForReport(reportWithEventProcessDefKey);
    String alertIdToRemain = alertClient.createAlertForReport(reportIdWithDefaultDefKey);

    String dashboardId = dashboardClient.createDashboard(
      collectionId, Arrays.asList(reportWithEventProcessDefKey, reportIdWithDefaultDefKey, reportIdWithNoDefKey)
    );

    List<String> eventProcessIds = Arrays.asList(
      firstDeletingEventProcessDefinitionKey,
      secondDeletingEventProcessDefinitionKey
    );

    // when
    eventProcessClient.createBulkDeleteEventProcessMappingsRequest(eventProcessIds).execute();
    embeddedOptimizeExtension.getEventBasedProcessesInstanceImportScheduler().runImportRound();

    // then
    assertGetMappingRequestStatusCode(
      firstDeletingEventProcessDefinitionKey,
      Response.Status.NOT_FOUND.getStatusCode()
    );
    assertThat(collectionClient.getReportsForCollection(collectionId))
      .extracting(AuthorizedReportDefinitionResponseDto.Fields.definitionDto + "." + ReportDefinitionDto.Fields.id)
      .containsExactlyInAnyOrder(reportIdWithDefaultDefKey, reportIdWithNoDefKey);
    assertThat(alertClient.getAlertsForCollectionAsDefaultUser(collectionId))
      .extracting(AlertDefinitionDto.Fields.id)
      .containsExactly(alertIdToRemain);
    assertThat(getAllCollectionDefinitions())
      .hasSize(1)
      .extracting(CollectionDefinitionDto.Fields.data + "." + CollectionDataDto.Fields.scope)
      .contains(Collections.singletonList(new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY)));
    assertThat(dashboardClient.getDashboard(dashboardId).getTiles())
      .extracting(DashboardReportTileDto.Fields.id)
      .containsExactlyInAnyOrder(reportIdWithDefaultDefKey, reportIdWithNoDefKey);
    assertThat(getEventProcessPublishStateDtoFromElasticsearch(firstDeletingEventProcessDefinitionKey)).isEmpty();
    assertThat(eventInstanceIndexForPublishStateExists(publishState)).isFalse();
    assertGetMappingRequestStatusCode(
      firstDeletingEventProcessDefinitionKey,
      Response.Status.NOT_FOUND.getStatusCode()
    );
    assertThat(getAllDocumentsOfVariableLabelIndex()).hasSize(1)
      .containsExactly(nonDeletedDefinitionVariableLabelsDto);
  }

  private EventProcessMappingDto createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource() {
    return eventProcessClient.buildEventProcessMappingDtoWithMappingsAndExternalEventSource(
      Collections.singletonMap(
        USER_TASK_ID_THREE,
        createEventMappingsDto(
          createMappedEventDto(),
          createMappedEventDto()
        )
      ),
      "process name",
      simpleDiagramXml
    );
  }

  @SneakyThrows
  private List<CollectionDefinitionDto> getAllCollectionDefinitions() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      COLLECTION_INDEX_NAME,
      CollectionDefinitionDto.class
    );
  }

  private void assertGetMappingRequestStatusCode(String eventProcessMappingKey, int statusCode) {
    eventProcessClient.createGetEventProcessMappingRequest(eventProcessMappingKey)
      .execute(statusCode);
  }

  private void executeUpdateProcessVariableLabelRequest(DefinitionVariableLabelsDto labelOptimizeDto) {
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableLabelRequest(labelOptimizeDto)
      .execute();
  }

  private List<DefinitionVariableLabelsDto> getAllDocumentsOfVariableLabelIndex() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      VARIABLE_LABEL_INDEX_NAME,
      DefinitionVariableLabelsDto.class
    );
  }

  private DefinitionVariableLabelsDto addLabelForEventProcessDefinition(final String eventProcessDefinitionKey,
                                                                        final LabelDto labelDto) {
    DefinitionVariableLabelsDto definitionVariableLabelsDto = new DefinitionVariableLabelsDto(
      eventProcessDefinitionKey,
      List.of(labelDto)
    );
    executeUpdateProcessVariableLabelRequest(definitionVariableLabelsDto);
    return definitionVariableLabelsDto;
  }
}
