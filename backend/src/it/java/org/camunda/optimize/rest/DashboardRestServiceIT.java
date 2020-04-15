/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static javax.ws.rs.HttpMethod.DELETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_SHARE_INDEX_NAME;
import static org.mockserver.model.HttpRequest.request;

public class DashboardRestServiceIT extends AbstractIT {

  @Test
  public void createNewDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildCreateDashboardRequest(generateDashboardDefinitionDto())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void createNewDashboard() {
    // when
    IdDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    // then the status code is okay
    assertThat(idDto).isNotNull();
  }

  @Test
  public void createNewDashboardWithDefinition() {
    // when
    IdDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(generateDashboardDefinitionDto())
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    // then the status code is okay
    assertThat(idDto).isNotNull();
  }

  @Test
  public void copyPrivateDashboard() {
    // given
    String dashboardId = createEmptyPrivateDashboard();
    createEmptyReportToDashboard(dashboardId);

    // when
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    // then
    DashboardDefinitionDto oldDashboard = getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = getDashboard(copyId.getId());
    assertThat(dashboard.toString()).isEqualTo(oldDashboard.toString());
    assertThat(dashboard.getName()).isEqualTo(oldDashboard.getName() + " – Copy");

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    final List<String> oldDashboardReportIds = oldDashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());
    assertThat(newReportIds).isNotEmpty();
    assertThat(newReportIds).containsExactlyInAnyOrderElementsOf(oldDashboardReportIds);
  }

  @Test
  public void copyPrivateDashboardWithNameParameter() {
    // given
    final String dashboardId = createEmptyPrivateDashboard();
    createEmptyReportToDashboard(dashboardId);

    final String testDashboardCopyName = "This is my new report copy! ;-)";

    // when
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("name", testDashboardCopyName)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    // then
    DashboardDefinitionDto oldDashboard = getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = getDashboard(copyId.getId());
    assertThat(dashboard.toString()).isEqualTo(oldDashboard.toString());
    assertThat(dashboard.getName()).isEqualTo(testDashboardCopyName);
  }

  @Test
  public void getDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetDashboardRequest("asdf")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getDashboard() {
    //given
    DashboardDefinitionDto definitionDto = generateDashboardDefinitionDto();
    String id = createDashboard(generateDashboardDefinitionDto());

    // when
    DashboardDefinitionDto returnedDashboard = getDashboardWithId(id);

    // then
    assertThat(returnedDashboard).isNotNull();
    assertThat(returnedDashboard.getId()).isEqualTo(id);
    assertThat(returnedDashboard.getName()).isEqualTo(definitionDto.getName());
  }

  @Test
  public void getDashboardForNonExistingIdThrowsError() {
    // when
    String response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDashboardRequest("fooid")
      .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());

    // then the status code is okay
    assertThat(response.contains("Dashboard does not exist!")).isTrue();
  }

  @Test
  public void updateDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildUpdateDashboardRequest("1", null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void updateNonExistingDashboard() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest("nonExistingId", new DashboardDefinitionDto())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void updateDashboard() {
    //given
    String id = createEmptyPrivateDashboard();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, new DashboardDefinitionDto())
      .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void updateDashboardDoesNotChangeCollectionId() {
    //given
    final String collectionId = createEmptyCollectionToOptimize();
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId(collectionId);
    String id = createDashboard(dashboardDefinitionDto);

    // when
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, new DashboardDefinitionDto())
      .execute();

    // then
    final DashboardDefinitionDto dashboard = getDashboard(id);
    assertThat(dashboard.getCollectionId()).isEqualTo(collectionId);
  }

  @Test
  public void deleteDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildDeleteDashboardRequest("1124")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void deleteNewDashboard() {
    //given
    String id = createEmptyPrivateDashboard();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteDashboardRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void deleteNonExistingDashboard() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteDashboardRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void deleteDashboardWithShares_shareAlsoGetsDeleted() {
    // given
    final String dashboardId = createDashboardWithDefinition();
    final String shareId = createDashboardShareForDashboard(dashboardId);

    // then
    assertThat(documentShareExists(shareId)).isTrue();

    // when
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteDashboardRequest(dashboardId)
      .execute(Response.Status.NO_CONTENT.getStatusCode());

    // then
    assertThat(documentShareExists(shareId)).isFalse();
  }

  @Test
  public void deleteDashboardWithShares_shareGetsDeleted_despiteDashboardDeleteFail() {
    // given
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    final String dashboardId = createDashboardWithDefinition();
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + DASHBOARD_INDEX_NAME + "/_doc/" + dashboardId)
      .withMethod(DELETE);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    final String shareId = createDashboardShareForDashboard(dashboardId);

    // then
    assertThat(documentShareExists(shareId)).isTrue();

    // when
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteDashboardRequest(dashboardId)
      .execute(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(getDashboardWithId(dashboardId)).isNotNull();
    assertThat(documentShareExists(shareId)).isFalse();
  }

  @Test
  public void deleteDashboardWithShares_shareDeleteFails_dashboardNotDeleted() {
    // given
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    final String dashboardId = createDashboardWithDefinition();
    final String shareId = createDashboardShareForDashboard(dashboardId);
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + DASHBOARD_SHARE_INDEX_NAME + "/_doc/" + shareId)
      .withMethod(DELETE);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // then
    assertThat(documentShareExists(shareId)).isTrue();

    // when
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteDashboardRequest(dashboardId)
      .execute(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(getDashboardWithId(dashboardId)).isNotNull();
    assertThat(documentShareExists(shareId)).isTrue();
  }

  private String createDashboardShareForDashboard(final String dashboardId) {
    DashboardShareDto sharingDto = new DashboardShareDto();
    sharingDto.setDashboardId(dashboardId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildShareDashboardRequest(sharingDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode()).getId();
  }

  @SneakyThrows
  private boolean documentShareExists(final String shareId) {
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(DASHBOARD_SHARE_INDEX_NAME);
    List<String> storedShareIds = new ArrayList<>();
    for (SearchHit searchHitFields : idsResp.getHits()) {
      storedShareIds.add(elasticSearchIntegrationTestExtension.getObjectMapper()
                           .readValue(searchHitFields.getSourceAsString(), DashboardShareDto.class).getId());
    }
    return storedShareIds.contains(shareId);
  }

  private String createEmptyPrivateDashboard() {
    return createDashboard(new DashboardDefinitionDto());
  }

  private String createDashboard(final DashboardDefinitionDto dashboardDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private DashboardDefinitionDto getDashboard(String dashboardId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDashboardRequest(dashboardId)
      .execute(DashboardDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  private String createEmptyCollectionToOptimize() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private void updateDashboardRequest(final String dashboardId, final List<ReportLocationDto> reports) {
    final DashboardDefinitionDto dashboard = getDashboardWithId(dashboardId);
    if (reports != null) {
      dashboard.setReports(reports);
    }
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboard)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  private DashboardDefinitionDto getDashboardWithId(final String id) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDashboardRequest(id)
      .execute(DashboardDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  private void createEmptyReportToDashboard(final String dashboardId) {
    final String reportId = createEmptySingleProcessReportToCollection();
    final ReportLocationDto reportLocationDto = new ReportLocationDto();
    reportLocationDto.setId(reportId);
    updateDashboardRequest(dashboardId, Collections.singletonList(reportLocationDto));
  }

  private String createEmptySingleProcessReportToCollection() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(new SingleProcessReportDefinitionDto())
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private String createDashboardWithDefinition() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(generateDashboardDefinitionDto())
      .execute(IdDto.class, Response.Status.OK.getStatusCode()).getId();
  }

  private DashboardDefinitionDto generateDashboardDefinitionDto() {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setName("Dashboard name");
    return dashboardDefinitionDto;
  }

}
