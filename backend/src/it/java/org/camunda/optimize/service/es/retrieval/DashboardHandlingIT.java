/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.retrieval;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static javax.ws.rs.HttpMethod.PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.mockserver.model.HttpRequest.request;

public class DashboardHandlingIT extends AbstractIT {

  @AfterEach
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @Test
  public void dashboardIsWrittenToElasticsearch() throws IOException {
    // given
    String id = addEmptyPrivateDashboard();

    // when
    GetRequest getRequest = new GetRequest(DASHBOARD_INDEX_NAME).id(id);
    GetResponse getResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient().get(getRequest);

    // then
    assertThat(getResponse.isExists()).isTrue();
  }

  @Test
  public void writeAndThenReadGivesTheSameResult() {
    // given
    String id = addEmptyPrivateDashboard();

    // when
    DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(id);

    // then
    assertThat(dashboard).isNotNull();
    assertThat(dashboard.getId()).isEqualTo(id);
  }

  @Test
  public void createAndGetSeveralDashboards() {
    // given
    String id1 = addEmptyPrivateDashboard();
    String id2 = addEmptyPrivateDashboard();

    // when
    DashboardDefinitionRestDto dashboard1 = dashboardClient.getDashboard(id1);
    DashboardDefinitionRestDto dashboard2 = dashboardClient.getDashboard(id2);

    // then
    assertThat(dashboard1).isNotNull();
    assertThat(dashboard1.getId()).isEqualTo(id1);
    assertThat(dashboard2).isNotNull();
    assertThat(dashboard2.getId()).isEqualTo(id2);
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    addEmptyReportToDashboard(dashboardId);

    final String collectionId = collectionClient.createNewCollection();

    // when
    IdResponseDto copyId = dashboardClient.copyDashboardToCollection(dashboardId, collectionId);

    // then
    DashboardDefinitionRestDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard.getName()).isEqualTo(oldDashboard.getName() + " – Copy");
    assertThat(dashboard.getCollectionId()).isEqualTo(collectionId);
    assertThat(oldDashboard.getCollectionId()).isNull();

    final List<String> newReportIds = dashboard.getTiles()
      .stream()
      .map(DashboardReportTileDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty()).isFalse();
    assertThat(newReportIds)
      .doesNotContainAnyElementsOf(oldDashboard.getTiles().stream()
                                     .map(DashboardReportTileDto::getId)
                                     .collect(Collectors.toList()));
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection_withExternalResource() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    final String reportId = createNewSingleProcessReport();
    final String externalResource = "http://www.camunda.com";
    dashboardClient.updateDashboardWithReports(dashboardId, Arrays.asList(reportId, externalResource));

    final String collectionId = collectionClient.createNewCollection();

    // when
    IdResponseDto copyId = dashboardClient.copyDashboardToCollection(dashboardId, collectionId);

    // then
    DashboardDefinitionRestDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard.getName()).isEqualTo(oldDashboard.getName() + " – Copy");
    assertThat(dashboard.getCollectionId()).isEqualTo(collectionId);
    assertThat(oldDashboard.getCollectionId()).isNull();

    final List<String> newReportIds = dashboard.getTiles()
      .stream()
      .map(DashboardReportTileDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty()).isFalse();
    assertThat(newReportIds).doesNotContain(reportId);
    assertThat(newReportIds).contains(externalResource);
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection_duplicateReportIsOnlyCopiedOnce() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    String reportId = createNewSingleProcessReport();
    dashboardClient.updateDashboardWithReports(dashboardId, Arrays.asList(reportId, reportId));

    final String collectionId = collectionClient.createNewCollection();

    // when
    IdResponseDto copyId = dashboardClient.copyDashboardToCollection(dashboardId, collectionId);

    // then
    DashboardDefinitionRestDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard.getName()).isEqualTo(oldDashboard.getName() + " – Copy");
    assertThat(dashboard.getCollectionId()).isEqualTo(collectionId);
    assertThat(oldDashboard.getCollectionId()).isNull();

    final List<String> newReportIds = dashboard.getTiles()
      .stream()
      .map(DashboardReportTileDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds).hasSize(2);
    assertThat(newReportIds).doesNotContainAnyElementsOf(oldDashboard.getTiles().stream()
                                                           .map(DashboardReportTileDto::getId)
                                                           .collect(Collectors.toList()));
    assertThat(newReportIds).allSatisfy(str -> assertThat(str).isEqualTo(newReportIds.get(0)));

    final List<EntityResponseDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);
    assertThat(collectionEntities).hasSize(2);
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection_reportsAreCopiedDespiteDashboardCreationFailureWithEsDown() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    String reportId = createNewSingleProcessReport();
    dashboardClient.updateDashboardWithReports(dashboardId, Arrays.asList(reportId, reportId));
    final String collectionId = collectionClient.createNewCollection();

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + DASHBOARD_INDEX_NAME + "/_doc/.*")
      .withMethod(PUT);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    final Response copyResponse = dashboardClient.copyDashboardToCollectionAsUserAndGetRawResponse(
      dashboardId, collectionId, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(copyResponse.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    DashboardDefinitionRestDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    assertThat(oldDashboard.getCollectionId()).isNull();
    assertThat(dashboardWithNameExists(oldDashboard.getName() + " – Copy")).isFalse();

    final List<EntityResponseDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);
    assertThat(collectionEntities)
      .hasSize(1)
      .extracting(EntityResponseDto::getName)
      .containsExactly("New Report – Copy");
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection_dashboardNotCreatedIfReportCopyFailsWithEsDown() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    String reportId = createNewSingleProcessReport();
    dashboardClient.updateDashboardWithReports(dashboardId, Arrays.asList(reportId, reportId));
    final String collectionId = collectionClient.createNewCollection();

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + SINGLE_PROCESS_REPORT_INDEX_NAME + "/_doc/.*")
      .withMethod(PUT);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    final Response copyResponse = dashboardClient.copyDashboardToCollectionAsUserAndGetRawResponse(
      dashboardId, collectionId, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(copyResponse.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    DashboardDefinitionRestDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    assertThat(oldDashboard.getCollectionId()).isNull();
    assertThat(dashboardWithNameExists(oldDashboard.getName() + " – Copy")).isFalse();

    final List<EntityResponseDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);
    assertThat(collectionEntities).isEmpty();
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection_duplicateReportIsOnlyCopiedOnceIfReferencedFromCombinedReport() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    String reportId = createNewSingleProcessReport();
    String combinedReportId = reportClient.createNewCombinedReport(reportId);
    dashboardClient.updateDashboardWithReports(dashboardId, Arrays.asList(reportId, combinedReportId));

    final String collectionId = collectionClient.createNewCollection();

    // when
    IdResponseDto copyId = dashboardClient.copyDashboardToCollection(dashboardId, collectionId);

    // then
    DashboardDefinitionRestDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard.getName()).isEqualTo(oldDashboard.getName() + " – Copy");
    assertThat(dashboard.getCollectionId()).isEqualTo(collectionId);
    assertThat(oldDashboard.getCollectionId()).isNull();

    final List<String> newReportIds = dashboard.getTiles()
      .stream()
      .map(DashboardReportTileDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds).hasSize(2);
    assertThat(newReportIds).doesNotContainAnyElementsOf(oldDashboard.getTiles().stream()
                                                           .map(DashboardReportTileDto::getId)
                                                           .collect(Collectors.toList()));

    final List<EntityResponseDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);
    assertThat(collectionEntities).hasSize(3);
  }

  @Test
  public void copyDashboardFromCollectionToPrivateEntities() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    addEmptyReportToDashboardInCollection(dashboardId, collectionId);

    // when
    IdResponseDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("collectionId", "null")
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    DashboardDefinitionRestDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard.getName()).isEqualTo(oldDashboard.getName() + " – Copy");
    assertThat(dashboard.getCollectionId()).isNull();
    assertThat(oldDashboard.getCollectionId()).isEqualTo(collectionId);

    final List<String> newReportIds = dashboard.getTiles()
      .stream()
      .map(DashboardReportTileDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds).isNotEmpty();
    assertThat(newReportIds).doesNotContainAnyElementsOf(oldDashboard.getTiles().stream()
                                                           .map(DashboardReportTileDto::getId)
                                                           .collect(Collectors.toList()));
  }

  @Test
  public void copyDashboardFromCollectionToDifferentCollection() {
    // given
    final String oldCollectionId = collectionClient.createNewCollection();
    String dashboardId = dashboardClient.createEmptyDashboard(oldCollectionId);
    addEmptyReportToDashboardInCollection(dashboardId, oldCollectionId);

    final String newCollectionId = collectionClient.createNewCollection();

    // when
    IdResponseDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("collectionId", newCollectionId)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    DashboardDefinitionRestDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionRestDto newDashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(newDashboard.getName()).isEqualTo(oldDashboard.getName() + " – Copy");
    assertThat(newDashboard.getCollectionId()).isEqualTo(newCollectionId);
    assertThat(oldDashboard.getCollectionId()).isEqualTo(oldCollectionId);

    final List<String> newReportIds = newDashboard.getTiles()
      .stream()
      .map(DashboardReportTileDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds).isNotEmpty();
    assertThat(newReportIds).doesNotContainAnyElementsOf(oldDashboard.getTiles().stream()
                                                           .map(DashboardReportTileDto::getId)
                                                           .collect(Collectors.toList()));
  }

  @Test
  public void updateDashboard() {
    // given
    final String shouldBeIgnoredString = "shouldNotBeUpdated";
    final OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    final String id = addEmptyPrivateDashboard();
    final String reportId = createNewSingleProcessReport();
    final DashboardReportTileDto dashboardTileDto = new DashboardReportTileDto();
    dashboardTileDto.setId(reportId);
    dashboardTileDto.setType(DashboardTileType.OPTIMIZE_REPORT);
    dashboardTileDto.setConfiguration("testConfiguration");

    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setTiles(Collections.singletonList(dashboardTileDto));
    dashboard.setId(shouldBeIgnoredString);
    dashboard.setLastModifier("shouldNotBeUpdatedManually");
    dashboard.setName("MyDashboard");
    dashboard.setCreated(shouldBeIgnoredDate);
    dashboard.setLastModified(shouldBeIgnoredDate);
    dashboard.setOwner(shouldBeIgnoredString);

    // when
    dashboardClient.updateDashboard(id, dashboard);
    DashboardDefinitionRestDto updatedDashboard = dashboardClient.getDashboard(id);

    // then
    assertThat(updatedDashboard.getTiles()).hasSize(1);
    DashboardReportTileDto retrievedLocation = updatedDashboard.getTiles().get(0);
    assertThat(retrievedLocation.getId()).isEqualTo(reportId);
    assertThat(retrievedLocation.getConfiguration()).isEqualTo("testConfiguration");
    assertThat(updatedDashboard.getId()).isEqualTo(id);
    assertThat(updatedDashboard.getCreated()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(updatedDashboard.getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(updatedDashboard.getLastModified()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(updatedDashboard.getName()).isEqualTo("MyDashboard");
    assertThat(updatedDashboard.getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @Test
  public void updateDashboardCollectionReportCanBeAddedToSameCollectionDashboard() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    final String privateReportId = reportClient.createEmptySingleProcessReportInCollection(collectionId);

    // when
    final Response updateResponse = addSingleReportToDashboard(dashboardId, privateReportId);

    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void updateDashboardCollectionReportCannotBeAddedToOtherCollectionDashboard() {
    // given
    String collectionId1 = collectionClient.createNewCollection();
    String collectionId2 = collectionClient.createNewCollection();
    String dashboardId = dashboardClient.createEmptyDashboard(collectionId1);
    final String privateReportId = reportClient.createEmptySingleProcessReportInCollection(collectionId2);

    // when
    final Response updateResponse = addSingleReportToDashboard(dashboardId, privateReportId);

    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateDashboardCollectionReportCannotBeAddedToPrivateDashboard() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String dashboardId = addEmptyPrivateDashboard();
    final String privateReportId = reportClient.createEmptySingleProcessReportInCollection(collectionId);

    // when
    final Response updateResponse = addSingleReportToDashboard(dashboardId, privateReportId);

    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateDashboardPrivateReportCannotBeAddedToCollectionDashboard() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    final String privateReportId = createNewSingleProcessReport();

    // when
    final Response updateResponse = addSingleReportToDashboard(dashboardId, privateReportId);

    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateDashboardAddingOtherUsersPrivateReportFails() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    String dashboardId = addEmptyPrivateDashboard();
    final String reportId = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateSingleProcessReportRequest()
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();

    // when
    final Response updateResponse = addSingleReportToDashboard(dashboardId, reportId);

    // then
    assertThat(updateResponse.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void doNotUpdateNullFieldsInDashboard() {
    // given
    String id = addEmptyPrivateDashboard();
    DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();

    // when
    dashboardClient.updateDashboard(id, dashboard);
    DashboardDefinitionRestDto updatedDashboard = dashboardClient.getDashboard(id);

    // then
    assertThat(updatedDashboard.getId()).isEqualTo(id);
    assertThat(updatedDashboard.getCreated()).isNotNull();
    assertThat(updatedDashboard.getLastModified()).isNotNull();
    assertThat(updatedDashboard.getLastModifier()).isNotNull();
    assertThat(updatedDashboard.getName()).isNotNull();
    assertThat(updatedDashboard.getOwner()).isNotNull();
  }

  @Test
  public void deletedSingleReportIsRemovedFromDashboardWhenForced() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    String reportIdToDelete = createNewSingleProcessReport();

    DashboardReportTileDto reportToBeDeletedDashboardTileDto = new DashboardReportTileDto();
    reportToBeDeletedDashboardTileDto.setId(reportIdToDelete);
    reportToBeDeletedDashboardTileDto.setType(DashboardTileType.OPTIMIZE_REPORT);
    reportToBeDeletedDashboardTileDto.setConfiguration("testConfiguration");
    DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setTiles(Collections.singletonList(reportToBeDeletedDashboardTileDto));
    dashboardClient.updateDashboard(dashboardId, dashboard);

    // when
    deleteReport(reportIdToDelete);

    // then
    DashboardDefinitionRestDto updatedDashboard = dashboardClient.getDashboard(dashboardId);
    updatedDashboard.getTiles().forEach(
      reportLocationDto -> assertThat(reportLocationDto.getId()).isNotEqualTo(reportIdToDelete));
  }

  private void deleteReport(String reportId) {
    Response response = reportClient.deleteReport(reportId, true);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  private String addEmptyPrivateDashboard() {
    return dashboardClient.createEmptyDashboard(null);
  }

  private void addEmptyReportToDashboard(final String dashboardId) {
    addEmptyReportToDashboardInCollection(dashboardId, null);
  }

  private void addEmptyReportToDashboardInCollection(final String dashboardId, final String collectionId) {
    final String reportId = reportClient.createEmptySingleProcessReportInCollection(collectionId);
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportId));
  }

  private String createNewSingleProcessReport() {
    return reportClient.createEmptySingleProcessReportInCollection(null);
  }

  private Response addSingleReportToDashboard(final String dashboardId, final String privateReportId) {
    final DashboardReportTileDto dashboardTileDto = new DashboardReportTileDto();
    dashboardTileDto.setId(privateReportId);
    dashboardTileDto.setType(DashboardTileType.OPTIMIZE_REPORT);

    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(
        dashboardId,
        new DashboardDefinitionRestDto(Collections.singletonList(dashboardTileDto))
      )
      .execute();
  }

  @SneakyThrows
  private boolean dashboardWithNameExists(final String dashboardName) {
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DASHBOARD_INDEX_NAME);
    List<String> storedDashboards = new ArrayList<>();
    for (SearchHit searchHitFields : idsResp.getHits()) {
      storedDashboards.add(elasticSearchIntegrationTestExtension.getObjectMapper()
                             .readValue(searchHitFields.getSourceAsString(), DashboardDefinitionRestDto.class).getName());
    }
    return storedDashboards.contains(dashboardName);
  }

}
