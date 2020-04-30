/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DashboardFilterType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  @ParameterizedTest
  @MethodSource("dashboardFilterCombinations")
  public void createNewDashboardWithFilterSpecification(List<DashboardFilterDto> dashboardFilterDtos) {
    // when
    final DashboardDefinitionDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    dashboardDefinitionDto.setAvailableFilters(dashboardFilterDtos);
    IdDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(idDto.getId()).isNotNull();
    final DashboardDefinitionDto savedDefinition = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDashboardRequest(idDto.getId())
      .execute(DashboardDefinitionDto.class, Response.Status.OK.getStatusCode());
    assertThat(savedDefinition.getAvailableFilters()).containsExactlyInAnyOrderElementsOf(dashboardFilterDtos);
  }

  @Test
  public void copyPrivateDashboard() {
    // given
    String dashboardId = dashboardClient.createEmptyDashboard(null);
    createEmptyReportToDashboard(dashboardId);

    // when
    IdDto copyId = dashboardClient.copyDashboard(dashboardId);

    // then
    DashboardDefinitionDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = dashboardClient.getDashboard(copyId.getId());
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
    final String dashboardId = dashboardClient.createEmptyDashboard(null);
    createEmptyReportToDashboard(dashboardId);

    final String testDashboardCopyName = "This is my new report copy! ;-)";

    // when
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("name", testDashboardCopyName)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    // then
    DashboardDefinitionDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = dashboardClient.getDashboard(copyId.getId());
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
    String id = dashboardClient.createDashboard(generateDashboardDefinitionDto());

    // when
    DashboardDefinitionDto returnedDashboard = dashboardClient.getDashboard(id);

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
    String id = dashboardClient.createEmptyDashboard(null);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, new DashboardDefinitionDto())
      .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("dashboardFilterCombinations")
  public void updateDashboardFilterSpecification(List<DashboardFilterDto> dashboardFilterDtos) {
    // when
    final DashboardDefinitionDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    IdDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(idDto.getId()).isNotNull();
    final DashboardDefinitionDto savedDefinition = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDashboardRequest(idDto.getId())
      .execute(DashboardDefinitionDto.class, Response.Status.OK.getStatusCode());
    assertThat(savedDefinition.getAvailableFilters().isEmpty());

    // when
    dashboardDefinitionDto.setAvailableFilters(dashboardFilterDtos);
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(idDto.getId(), dashboardDefinitionDto)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
    final DashboardDefinitionDto updatedDefinition = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDashboardRequest(idDto.getId())
      .execute(DashboardDefinitionDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(updatedDefinition.getId()).isEqualTo(savedDefinition.getId());
    assertThat(updatedDefinition.getAvailableFilters()).containsExactlyInAnyOrderElementsOf(dashboardFilterDtos);
  }

  @Test
  public void updateDashboardDoesNotChangeCollectionId() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId(collectionId);
    String id = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when
    dashboardClient.updateDashboard(id, new DashboardDefinitionDto());

    // then
    final DashboardDefinitionDto dashboard = dashboardClient.getDashboard(id);
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
    String id = dashboardClient.createEmptyDashboard(null);

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
    final String dashboardId = dashboardClient.createDashboard(generateDashboardDefinitionDto());
    final String shareId = dashboardClient.createDashboardShareForDashboard(dashboardId);

    // then
    assertThat(documentShareExists(shareId)).isTrue();

    // when
    dashboardClient.deleteDashboard(dashboardId);

    // then
    assertThat(documentShareExists(shareId)).isFalse();
  }

  @Test
  public void deleteDashboardWithShares_shareGetsDeleted_despiteDashboardDeleteFail() {
    // given
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    final String dashboardId = dashboardClient.createDashboard(generateDashboardDefinitionDto());
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + DASHBOARD_INDEX_NAME + "/_doc/" + dashboardId)
      .withMethod(DELETE);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    final String shareId = dashboardClient.createDashboardShareForDashboard(dashboardId);

    // then
    assertThat(documentShareExists(shareId)).isTrue();

    // when
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteDashboardRequest(dashboardId)
      .execute(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(dashboardClient.getDashboard(dashboardId)).isNotNull();
    assertThat(documentShareExists(shareId)).isFalse();
  }

  @Test
  public void deleteDashboardWithShares_shareDeleteFails_dashboardNotDeleted() {
    // given
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    final String dashboardId = dashboardClient.createDashboard(generateDashboardDefinitionDto());
    final String shareId = dashboardClient.createDashboardShareForDashboard(dashboardId);
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
    assertThat(dashboardClient.getDashboard(dashboardId)).isNotNull();
    assertThat(documentShareExists(shareId)).isTrue();
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

  private static Stream<List<DashboardFilterDto>> dashboardFilterCombinations() {
    return Stream.of(
      Collections.emptyList(),
      Collections.singletonList(new DashboardFilterDto(DashboardFilterType.START_DATE)),
      Arrays.asList(
        new DashboardFilterDto(DashboardFilterType.START_DATE),
        new DashboardFilterDto(DashboardFilterType.END_DATE)
      ),
      Arrays.asList(
        new DashboardFilterDto(DashboardFilterType.START_DATE),
        new DashboardFilterDto(DashboardFilterType.END_DATE),
        new DashboardFilterDto(DashboardFilterType.STATE)
      )
    );
  }

  private void createEmptyReportToDashboard(final String dashboardId) {
    final String reportId = reportClient.createEmptySingleProcessReportInCollection(null);
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportId));
  }

  private DashboardDefinitionDto generateDashboardDefinitionDto() {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setName("Dashboard name");
    return dashboardDefinitionDto;
  }

}
