/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.rest.eventprocess.EventBasedProcessRestServiceIT.createProcessDefinitionXml;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.EventProcessClient.createEventMappingsDto;
import static org.camunda.optimize.test.optimize.EventProcessClient.createMappedEventDto;

public class EventBasedProcessConflictIT extends AbstractEventProcessIT {

  private static String simpleDiagramXml;

  @BeforeAll
  public static void setup() {
    simpleDiagramXml = createProcessDefinitionXml();
  }

  @Test
  public void getDeleteConflictsForEventProcessMapping_withoutAuthentication() {
    // when
    Response response = eventProcessClient
      .createGetDeleteConflictsForEventProcessMappingRequest("doesNotMatter")
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getDeleteConflictsForEventProcessMapping_returnsOnlyConflictedItems() {
    // given a published event process with various dependent resources created using its definition
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

    String conflictedReportId = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(
        collectionId,
        eventProcessDefinitionKey,
        Collections.emptyList()
      ));
    String nonConflictedReportId = reportClient.createSingleProcessReport(
      reportClient.createSingleProcessReportDefinitionDto(
        collectionId,
        DEFAULT_DEFINITION_KEY,
        Collections.emptyList()
      ));
    String conflictedCombinedReportId =
      reportClient.createCombinedReport(collectionId, Arrays.asList(conflictedReportId, nonConflictedReportId));

    String conflictedAlertId = alertClient.createAlertForReport(conflictedReportId);
    alertClient.createAlertForReport(nonConflictedReportId);

    String conflictedDashboardId = dashboardClient.createDashboard(
      collectionId, Arrays.asList(conflictedReportId, nonConflictedReportId)
    );

    // when
    ConflictResponseDto conflictResponseDto =
      eventProcessClient.getDeleteConflictsForEventProcessMapping(eventProcessDefinitionKey);

    // then
    assertThat(conflictResponseDto.getConflictedItems())
      .extracting(ConflictedItemDto.Fields.id, ConflictedItemDto.Fields.type)
      .containsExactlyInAnyOrder(
        new Tuple(conflictedReportId, ConflictedItemType.REPORT),
        new Tuple(conflictedCombinedReportId, ConflictedItemType.COMBINED_REPORT),
        new Tuple(conflictedDashboardId, ConflictedItemType.DASHBOARD),
        new Tuple(conflictedAlertId, ConflictedItemType.ALERT)
      );
  }

  @Test
  public void checkDeleteConflictsForBulkDeleteOfEventProcessMappings_withUnauthorizedUser() {
    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckBulkDeleteConflictsForEventProcessMappingRequest(new ArrayList<>())
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void checkDeleteConflictsForBulkDeleteOfEventProcessMappings_nullEventBasedProcessList() {
    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckBulkDeleteConflictsForEventProcessMappingRequest(null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void bulkDeleteConflictsForEventProcessMapping_alertConflict() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey1 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey2 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey3 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey1);

    String collectionId = createNewCollectionWithScope(eventProcessDefinitionKey1);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    String reportId1 = reportClient.createSingleReport(
      collectionId,
      PROCESS,
      eventProcessDefinitionKey1,
      Collections.emptyList()
    );
    alertClient.createAlertForReport(reportId1);

    // when
    boolean response = eventProcessClient.eventProcessMappingRequestBulkDeleteHasConflicts(
      Arrays.asList(eventProcessDefinitionKey1, eventProcessDefinitionKey2, eventProcessDefinitionKey3));

    // then
    assertThat(response).isTrue();
  }

  @Test
  public void bulkDeleteConflictsForEventProcessMapping_combinedReportConflict() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey1 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey2 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey3 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey1);
    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey2);
    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey3);

    String collectionId = createNewCollectionWithScope(eventProcessDefinitionKey1);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    String reportId1 = reportClient.createSingleReport(
      collectionId,
      PROCESS,
      eventProcessDefinitionKey1,
      Collections.emptyList()
    );
    String reportId2 = reportClient.createSingleReport(
      collectionId,
      PROCESS,
      eventProcessDefinitionKey1,
      Collections.emptyList()
    );

    reportClient.createCombinedReport(collectionId, Arrays.asList(reportId1, reportId2));

    // when
    boolean response = eventProcessClient.eventProcessMappingRequestBulkDeleteHasConflicts(
      Arrays.asList(eventProcessDefinitionKey1, eventProcessDefinitionKey2, eventProcessDefinitionKey3));

    // then
    assertThat(response).isTrue();
  }

  @Test
  public void bulkDeleteConflictsForEventProcessMapping_dashboardConflict() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey1 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey2 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey3 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    eventProcessClient.publishEventProcessMapping(eventProcessDefinitionKey1);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    String collectionId = createNewCollectionWithScope(eventProcessDefinitionKey1);

    String reportId1 = reportClient.createSingleReport(
      collectionId,
      PROCESS,
      eventProcessDefinitionKey1,
      Collections.emptyList()
    );
    String reportId2 = reportClient.createEmptySingleProcessReport();
    dashboardClient.createDashboard(collectionId, Arrays.asList(reportId1, reportId2));

    // when
    boolean response = eventProcessClient.eventProcessMappingRequestBulkDeleteHasConflicts(
      Arrays.asList(eventProcessDefinitionKey1, eventProcessDefinitionKey2, eventProcessDefinitionKey3));

    // then
    assertThat(response).isTrue();
  }

  @Test
  public void bulkDeleteConflictsForEventProcessMapping_noConflicts() {
    // given
    EventProcessMappingDto eventProcessMappingDto =
      createEventProcessMappingDtoWithSimpleMappingsAndExternalEventSource();
    String eventProcessDefinitionKey1 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey2 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);
    String eventProcessDefinitionKey3 = eventProcessClient.createEventProcessMapping(eventProcessMappingDto);

    // when
    boolean response = eventProcessClient.eventProcessMappingRequestBulkDeleteHasConflicts(
      Arrays.asList(eventProcessDefinitionKey1, eventProcessDefinitionKey2, eventProcessDefinitionKey3));

    // then
    assertThat(response).isFalse();
  }

  private String createNewCollectionWithScope(String eventProcessDefinitionKey) {
    return collectionClient.createNewCollectionWithScope(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      PROCESS,
      eventProcessDefinitionKey,
      Collections.emptyList()
    );
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

}
