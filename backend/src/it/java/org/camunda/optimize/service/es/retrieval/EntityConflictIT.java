/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.entity.EntitiesDeleteRequestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class EntityConflictIT extends AbstractIT {

  @Test
  public void checkBulkDeleteConflicts_failsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckEntityDeleteConflictsRequest(new EntitiesDeleteRequestDto())
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void checkBulkDeleteConflicts_reportHasDeleteConflictsIfUsedByCombinedReport() {
    // given
    String reportId1 = reportClient.createEmptySingleProcessReport();
    String reportId2 = reportClient.createEmptySingleProcessReport();
    String reportId3 = reportClient.createEmptySingleProcessReport();

    reportClient.createCombinedReport(
      null,
      Arrays.asList(
        reportId1,
        reportId2
      )
    );

    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Arrays.asList(reportId2, reportId3),
      Collections.emptyList(),
      Collections.emptyList()
    );

    // when
    boolean response = entitiesClient.entitiesHaveDeleteConflicts(entitiesDeleteRequestDto);

    // then
    assertThat(response).isTrue();
  }

  @Test
  public void checkBulkDeleteConflicts_reportHasDeleteConflictsIfReportExistsInDashboard() {
    // given
    String reportId1 = reportClient.createEmptySingleProcessReport();
    String reportId2 = reportClient.createEmptySingleProcessReport();
    createNewDashboardAndAddReport(reportId1);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Arrays.asList(reportId1, reportId2),
      Collections.emptyList(),
      Collections.emptyList()
    );

    // when
    boolean response = entitiesClient.entitiesHaveDeleteConflicts(entitiesDeleteRequestDto);

    // then
    assertThat(response).isTrue();
  }

  @Test
  public void checkBulkDeleteConflicts_hasNoDeleteConflictsIfReportInDashboardAndDashboardGetsDeleted() {
    // given
    String dashboardId1 = dashboardClient.createEmptyDashboard(null);
    String reportId = reportClient.createEmptySingleProcessReport();
    String dashboardId2 = createNewDashboardAndAddReport(reportId);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Collections.singletonList(reportId),
      Collections.emptyList(),
      Arrays.asList(dashboardId1, dashboardId2)
    );

    // when
    boolean response = entitiesClient.entitiesHaveDeleteConflicts(entitiesDeleteRequestDto);

    // then
    assertThat(response).isFalse();
  }

  @Test
  public void checkBulkDeleteConflicts_hasNoDeleteConflictsIfReportInCombinedReportAndCombinedReportGetsDeleted() {
    // given
    String reportId1 = reportClient.createEmptySingleProcessReport();
    String reportId2 = reportClient.createEmptySingleProcessReport();
    String combinedReport = reportClient.createCombinedReport(
      null,
      Arrays.asList(
        reportId1,
        reportId2
      )
    );

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Arrays.asList(reportId1, combinedReport),
      Collections.emptyList(),
      Collections.emptyList()
    );

    // when
    boolean response = entitiesClient.entitiesHaveDeleteConflicts(entitiesDeleteRequestDto);

    // then
    assertThat(response).isFalse();
  }

  @Test
  public void checkBulkDeleteConflicts_hasDeleteConflictsIfUsedByAlertInCollection() {
    // given
    String collectionId1 = collectionClient.createNewCollection();
    String collectionId2 = collectionClient.createNewCollectionWithDefaultProcessScope();
    String reportId = reportClient.createEmptySingleProcessReportInCollection(collectionId2);
    alertClient.createAlertForReport(reportId);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Collections.singletonList(reportId),
      Arrays.asList(collectionId1, collectionId2),
      Collections.emptyList()
    );

    // when
    boolean response = entitiesClient.entitiesHaveDeleteConflicts(entitiesDeleteRequestDto);

    // then
    assertThat(response).isTrue();
  }

  @Test
  public void checkBulkDeleteConflicts_hasNoConflicts() {
    // given
    String collectionId1 = collectionClient.createNewCollection();
    String collectionId2 = collectionClient.createNewCollection();
    String collectionId3 = collectionClient.createNewCollectionWithDefaultProcessScope();
    String dashboardId1 = dashboardClient.createEmptyDashboard(null);
    String dashboardId2 = dashboardClient.createEmptyDashboard(null);
    String reportId = reportClient.createEmptySingleDecisionReportInCollection(collectionId3);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Collections.singletonList(reportId),
      Arrays.asList(collectionId1, collectionId2),
      Arrays.asList(dashboardId1, dashboardId2)
    );

    // when
    boolean response = entitiesClient.entitiesHaveDeleteConflicts(entitiesDeleteRequestDto);

    // then
    assertThat(response).isFalse();
  }

  @Test
  public void checkBulkDeleteConflicts_hasNoConflictsWhenEntityListsAreEmpty() {
    // given
    List<String> entities = Collections.emptyList();
    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      entities,
      entities,
      entities
    );

    // when
    boolean response = entitiesClient.entitiesHaveDeleteConflicts(entitiesDeleteRequestDto);

    // then
    assertThat(response).isFalse();
  }

  @Test
  public void checkBulkDeleteConflicts_returnsBadRequestWhenEntitiesAreNull() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckEntityDeleteConflictsRequest(null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void checkBulkDeleteConflicts_userIsNotAuthorizedToAccessCollectionOnReportConflictCheck() {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    String reportId1 = reportClient.createEmptySingleDecisionReportInCollection(collectionId);
    String reportId2 = reportClient.createEmptySingleProcessReport();
    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Arrays.asList(reportId1, reportId2),
      Collections.emptyList(),
      Collections.emptyList()
    );

    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckEntityDeleteConflictsRequest(entitiesDeleteRequestDto)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void checkBulkDeleteConflicts_userIsNotAuthorizedToAccessCollectionOnCollectionConflictCheck() {
    // given
    String collectionId1 = collectionClient.createNewCollectionWithDefaultProcessScope();
    String collectionId2 = collectionClient.createNewCollectionWithDefaultProcessScope();
    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Collections.emptyList(),
      Arrays.asList(collectionId1, collectionId2),
      Collections.emptyList()
    );

    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckEntityDeleteConflictsRequest(entitiesDeleteRequestDto)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void checkBulkDeleteConflicts_userIsNotAuthorizedToAccessCollectionOnDashboardConflictCheck() {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    String dashboardId1 = dashboardClient.createEmptyDashboard(collectionId);
    String dashboardId2 = dashboardClient.createEmptyDashboard(collectionId);
    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Collections.emptyList(),
      Collections.emptyList(),
      Arrays.asList(dashboardId1, dashboardId2)
    );

    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckEntityDeleteConflictsRequest(entitiesDeleteRequestDto)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void checkBulkDeleteConflicts_userIsAuthorizedToAccessConflictsForDashboardOutsideOfCollection() {
    // given
    String dashboardId1 = dashboardClient.createEmptyDashboard(null);
    String dashboardId2 = dashboardClient.createEmptyDashboard(null);
    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Collections.emptyList(),
      Collections.emptyList(),
      Arrays.asList(dashboardId1, dashboardId2)
    );

    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    boolean response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckEntityDeleteConflictsRequest(entitiesDeleteRequestDto)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(Boolean.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(response).isFalse();
  }

  @Test
  public void checkBulkDeleteConflicts_verifyNullEntitiesReturnBadRequest() {
    // given
    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      null,
      null,
      null
    );

    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckEntityDeleteConflictsRequest(entitiesDeleteRequestDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void checkBulkDeleteConflicts_verifyNullDashboardListReturnsBadRequest() {
    // given
    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Collections.emptyList(),
      Collections.emptyList(),
      null
    );

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckEntityDeleteConflictsRequest(entitiesDeleteRequestDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void checkBulkDeleteConflicts_verifyNullCollectionsListReturnsBadRequest() {
    // given
    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      Collections.emptyList(),
      null,
      Collections.emptyList()
    );

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckEntityDeleteConflictsRequest(entitiesDeleteRequestDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void checkBulkDeleteConflicts_verifyNullReportsListReturnsBadRequest() {
    // given
    EntitiesDeleteRequestDto entitiesDeleteRequestDto = new EntitiesDeleteRequestDto(
      null,
      Collections.emptyList(),
      Collections.emptyList()
    );

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCheckEntityDeleteConflictsRequest(entitiesDeleteRequestDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private String createNewDashboardAndAddReport(String reportId) {
    String id = dashboardClient.createEmptyDashboard(null);
    dashboardClient.updateDashboardWithReports(id, Collections.singletonList(reportId));
    return id;
  }

}
