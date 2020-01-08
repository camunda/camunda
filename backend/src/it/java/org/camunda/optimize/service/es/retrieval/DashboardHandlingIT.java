/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;

public class DashboardHandlingIT extends AbstractIT {

  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension =
    new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  public AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

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
    GetResponse getResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .get(getRequest, RequestOptions.DEFAULT);

    // then
    assertThat(getResponse.isExists(), is(true));
  }

  @Test
  public void writeAndThenReadGivesTheSameResult() {
    // given
    String id = addEmptyPrivateDashboard();

    // when
    DashboardDefinitionDto dashboard = dashboardClient.getDashboard(id);

    // then
    assertThat(dashboard, is(notNullValue()));
    assertThat(dashboard.getId(), is(id));
  }

  @Test
  public void createAndGetSeveralDashboards() {
    // given
    String id1 = addEmptyPrivateDashboard();
    String id2 = addEmptyPrivateDashboard();

    // when
    DashboardDefinitionDto dashboard1 = dashboardClient.getDashboard(id1);
    DashboardDefinitionDto dashboard2 = dashboardClient.getDashboard(id2);

    // then
    assertThat(dashboard1, is(notNullValue()));
    assertThat(dashboard1.getId(), is(id1));
    assertThat(dashboard2, is(notNullValue()));
    assertThat(dashboard2.getId(), is(id2));
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    addEmptyReportToDashboard(dashboardId);

    final String collectionId = collectionClient.createNewCollection();

    // when
    IdDto copyId = dashboardClient.copyDashboardToCollection(dashboardId, collectionId);

    // then
    DashboardDefinitionDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(dashboard.getCollectionId(), is(collectionId));
    assertThat(oldDashboard.getCollectionId(), is(nullValue()));

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(
      newReportIds,
      not(containsInAnyOrder(oldDashboard.getReports().stream().map(ReportLocationDto::getId).toArray()))
    );
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection_withExternalResource() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    final String reportId = addEmptySingleProcessReport();
    final String externalResource = "http://www.camunda.com";
    dashboardClient.updateDashboardWithReports(dashboardId, Arrays.asList(reportId, externalResource));

    final String collectionId = collectionClient.createNewCollection();

    // when
    IdDto copyId = dashboardClient.copyDashboardToCollection(dashboardId, collectionId);

    // then
    DashboardDefinitionDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(dashboard.getCollectionId(), is(collectionId));
    assertThat(oldDashboard.getCollectionId(), is(nullValue()));

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(newReportIds, not(hasItem(reportId)));
    assertThat(newReportIds, hasItem(externalResource));
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection_duplicateReportIsOnlyCopiedOnce() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    String reportId = addEmptySingleProcessReport();
    dashboardClient.updateDashboardWithReports(dashboardId, Arrays.asList(reportId, reportId));

    final String collectionId = collectionClient.createNewCollection();

    // when
    IdDto copyId = dashboardClient.copyDashboardToCollection(dashboardId, collectionId);

    // then
    DashboardDefinitionDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(dashboard.getCollectionId(), is(collectionId));
    assertThat(oldDashboard.getCollectionId(), is(nullValue()));

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.size(), is(2));
    assertThat(
      newReportIds,
      not(containsInAnyOrder(oldDashboard.getReports().stream().map(ReportLocationDto::getId).toArray()))
    );
    assertThat(newReportIds, everyItem(is(newReportIds.get(0))));

    final List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);
    assertThat(collectionEntities.size(), is(2));
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection_duplicateReportIsOnlyCopiedOnceIfReferencedFromCombinedReport() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    String reportId = addEmptySingleProcessReport();
    String combinedReportId = reportClient.createNewCombinedReport(reportId);
    dashboardClient.updateDashboardWithReports(dashboardId, Arrays.asList(reportId, combinedReportId));

    final String collectionId = collectionClient.createNewCollection();

    // when
    IdDto copyId = dashboardClient.copyDashboardToCollection(dashboardId, collectionId);

    // then
    DashboardDefinitionDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(dashboard.getCollectionId(), is(collectionId));
    assertThat(oldDashboard.getCollectionId(), is(nullValue()));

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.size(), is(2));
    assertThat(
      newReportIds,
      not(containsInAnyOrder(oldDashboard.getReports().stream().map(ReportLocationDto::getId).toArray()))
    );

    final List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);
    assertThat(collectionEntities.size(), is(3));
  }

  @Test
  public void copyDashboardFromCollectionToPrivateEntities() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    String dashboardId = addEmptyDashboardToCollectionAsDefaultUser(collectionId);
    addEmptyReportToDashboardInCollection(dashboardId, collectionId);

    // when
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("collectionId", "null")
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(dashboard.getCollectionId(), is(nullValue()));
    assertThat(oldDashboard.getCollectionId(), is(collectionId));

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(
      newReportIds,
      not(containsInAnyOrder(oldDashboard.getReports().stream().map(ReportLocationDto::getId).toArray()))
    );
  }

  @Test
  public void copyDashboardFromCollectionToDifferentCollection() {
    // given
    final String oldCollectionId = collectionClient.createNewCollection();
    String dashboardId = addEmptyDashboardToCollectionAsDefaultUser(oldCollectionId);
    addEmptyReportToDashboardInCollection(dashboardId, oldCollectionId);

    final String newCollectionId = collectionClient.createNewCollection();

    // when
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("collectionId", newCollectionId)
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionDto newDashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(newDashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(newDashboard.getCollectionId(), is(newCollectionId));
    assertThat(oldDashboard.getCollectionId(), is(oldCollectionId));

    final List<String> newReportIds = newDashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(
      newReportIds,
      not(containsInAnyOrder(oldDashboard.getReports().stream().map(ReportLocationDto::getId).toArray()))
    );
  }

  @Test
  public void updateDashboard() {
    // given
    final String shouldBeIgnoredString = "shouldNotBeUpdated";
    final OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    final String id = addEmptyPrivateDashboard();
    final String reportId = addEmptySingleProcessReport();
    final ReportLocationDto reportLocationDto = new ReportLocationDto();
    reportLocationDto.setId(reportId);
    reportLocationDto.setConfiguration("testConfiguration");

    final DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setReports(Collections.singletonList(reportLocationDto));
    dashboard.setId(shouldBeIgnoredString);
    dashboard.setLastModifier("shouldNotBeUpdatedManually");
    dashboard.setName("MyDashboard");
    dashboard.setCreated(shouldBeIgnoredDate);
    dashboard.setLastModified(shouldBeIgnoredDate);
    dashboard.setOwner(shouldBeIgnoredString);

    // when
    updateDashboard(id, dashboard);
    DashboardDefinitionDto updatedDashboard = dashboardClient.getDashboard(id);

    // then
    assertThat(updatedDashboard.getReports().size(), is(1));
    ReportLocationDto retrievedLocation = updatedDashboard.getReports().get(0);
    assertThat(retrievedLocation.getId(), is(reportId));
    assertThat(retrievedLocation.getConfiguration(), is("testConfiguration"));
    assertThat(updatedDashboard.getId(), is(id));
    assertThat(updatedDashboard.getCreated(), is(not(shouldBeIgnoredDate)));
    assertThat(updatedDashboard.getLastModifier(), is(DEFAULT_USERNAME));
    assertThat(updatedDashboard.getLastModified(), is(not(shouldBeIgnoredDate)));
    assertThat(updatedDashboard.getName(), is("MyDashboard"));
    assertThat(updatedDashboard.getOwner(), is(DEFAULT_USERNAME));
  }

  @Test
  public void updateDashboardCollectionReportCanBeAddedToSameCollectionDashboard() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String dashboardId = addEmptyDashboardToCollectionAsDefaultUser(collectionId);
    final String privateReportId = addEmptySingleProcessReportToCollection(collectionId);

    // when
    final Response updateResponse = addSingleReportToDashboard(dashboardId, privateReportId);

    // then
    assertThat(updateResponse.getStatus(), is(204));
  }

  @Test
  public void updateDashboardCollectionReportCannotBeAddedToOtherCollectionDashboard() {
    // given
    String collectionId1 = collectionClient.createNewCollection();
    String collectionId2 = collectionClient.createNewCollection();
    String dashboardId = addEmptyDashboardToCollectionAsDefaultUser(collectionId1);
    final String privateReportId = addEmptySingleProcessReportToCollection(collectionId2);

    // when
    final Response updateResponse = addSingleReportToDashboard(dashboardId, privateReportId);

    // then
    assertThat(updateResponse.getStatus(), is(400));
  }

  @Test
  public void updateDashboardCollectionReportCannotBeAddedToPrivateDashboard() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String dashboardId = addEmptyPrivateDashboard();
    final String privateReportId = addEmptySingleProcessReportToCollection(collectionId);

    // when
    final Response updateResponse = addSingleReportToDashboard(dashboardId, privateReportId);

    // then
    assertThat(updateResponse.getStatus(), is(400));
  }

  @Test
  public void updateDashboardPrivateReportCannotBeAddedToCollectionDashboard() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String dashboardId = addEmptyDashboardToCollectionAsDefaultUser(collectionId);
    final String privateReportId = createNewSingleReport();

    // when
    final Response updateResponse = addSingleReportToDashboard(dashboardId, privateReportId);

    // then
    assertThat(updateResponse.getStatus(), is(400));
  }

  @Test
  public void updateDashboardAddingOtherUsersPrivateReportFails() {
    //given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    String dashboardId = addEmptyPrivateDashboard();
    final String reportId = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();

    // when
    final Response updateResponse = addSingleReportToDashboard(dashboardId, reportId);

    // then
    assertThat(updateResponse.getStatus(), is(403));
  }

  @Test
  public void doNotUpdateNullFieldsInDashboard() {
    // given
    String id = addEmptyPrivateDashboard();
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();

    // when
    updateDashboard(id, dashboard);
    DashboardDefinitionDto updatedDashboard = dashboardClient.getDashboard(id);

    // then
    assertThat(updatedDashboard.getId(), is(id));
    assertThat(updatedDashboard.getCreated(), is(notNullValue()));
    assertThat(updatedDashboard.getLastModified(), is(notNullValue()));
    assertThat(updatedDashboard.getLastModifier(), is(notNullValue()));
    assertThat(updatedDashboard.getName(), is(notNullValue()));
    assertThat(updatedDashboard.getOwner(), is(notNullValue()));
  }

  @Test
  public void deletedSingleReportIsRemovedFromDashboardWhenForced() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    String reportIdToDelete = createNewSingleReport();

    ReportLocationDto reportToBeDeletedReportLocationDto = new ReportLocationDto();
    reportToBeDeletedReportLocationDto.setId(reportIdToDelete);
    reportToBeDeletedReportLocationDto.setConfiguration("testConfiguration");
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setReports(Collections.singletonList(reportToBeDeletedReportLocationDto));
    updateDashboard(dashboardId, dashboard);

    // when
    deleteReport(reportIdToDelete, true);

    // then
    DashboardDefinitionDto updatedDashboard = dashboardClient.getDashboard(dashboardId);
    updatedDashboard.getReports().forEach(
      reportLocationDto -> assertThat(reportLocationDto.getId(), is(not(reportIdToDelete)))
    );
  }

  private void deleteReport(String reportId, Boolean force) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private String addEmptyPrivateDashboard() {
    return addEmptyDashboardToCollectionAsDefaultUser(null);
  }

  private String addEmptyDashboardToCollectionAsDefaultUser(final String collectionId) {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateDashboard(String id, DashboardDefinitionDto updatedDashboard) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, updatedDashboard)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private String createNewSingleReport() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void addEmptyReportToDashboard(final String dashboardId) {
    addEmptyReportToDashboardInCollection(dashboardId, null);
  }

  private void addEmptyReportToDashboardInCollection(final String dashboardId, final String collectionId) {
    final String reportId = addEmptySingleProcessReportToCollection(collectionId);
    dashboardClient.updateDashboardWithReports(dashboardId, Arrays.asList(reportId));
  }

  private String addEmptySingleProcessReport() {
    return addEmptySingleProcessReportToCollection(null);
  }

  private String addEmptySingleProcessReportToCollection(final String collectionId) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private Response addSingleReportToDashboard(final String dashboardId, final String privateReportId) {
    final ReportLocationDto reportLocationDto = new ReportLocationDto();
    reportLocationDto.setId(privateReportId);

    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(
        dashboardId,
        new DashboardDefinitionDto(Collections.singletonList(reportLocationDto))
      )
      .execute();
  }
}
