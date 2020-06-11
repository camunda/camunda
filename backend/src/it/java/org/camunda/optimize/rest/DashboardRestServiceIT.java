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
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DoubleVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.IntegerVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.LongVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.ShortVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
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
import java.time.OffsetDateTime;
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
  @MethodSource("validFilterCombinations")
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
    if (dashboardFilterDtos == null) {
      assertThat(savedDefinition.getAvailableFilters()).isNull();
    } else {
      assertThat(savedDefinition.getAvailableFilters()).containsExactlyInAnyOrderElementsOf(dashboardFilterDtos);
    }
  }

  @ParameterizedTest
  @MethodSource("invalidFilterCombinations")
  public void createNewDashboardWithInvalidFilterSpecification(List<DashboardFilterDto> dashboardFilterDtos) {
    // when
    final DashboardDefinitionDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    dashboardDefinitionDto.setAvailableFilters(dashboardFilterDtos);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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
  @MethodSource("validFilterCombinations")
  public void updateDashboardFilterSpecification(List<DashboardFilterDto> dashboardFilterDtos) {
    // when
    final DashboardDefinitionDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // then
    assertThat(dashboardId).isNotNull();
    final DashboardDefinitionDto savedDefinition = dashboardClient.getDashboard(dashboardId);
    assertThat(savedDefinition.getAvailableFilters()).isEmpty();

    // when
    dashboardDefinitionDto.setAvailableFilters(dashboardFilterDtos);
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinitionDto)
      .execute();
    final DashboardDefinitionDto updatedDefinition = dashboardClient.getDashboard(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(updatedDefinition.getId()).isEqualTo(savedDefinition.getId());
    if (dashboardFilterDtos == null) {
      assertThat(updatedDefinition.getAvailableFilters()).isEmpty();
    } else {
      assertThat(updatedDefinition.getAvailableFilters()).containsExactlyInAnyOrderElementsOf(dashboardFilterDtos);
    }
  }

  @ParameterizedTest
  @MethodSource("invalidFilterCombinations")
  public void updateDashboardFilterSpecification_invalidFilters(List<DashboardFilterDto> dashboardFilterDtos) {
    // when
    final DashboardDefinitionDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    IdDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(idDto.getId()).isNotNull();
    final DashboardDefinitionDto savedDefinition = dashboardClient.getDashboard(idDto.getId());
    assertThat(savedDefinition.getAvailableFilters()).isEmpty();

    // when
    dashboardDefinitionDto.setAvailableFilters(dashboardFilterDtos);
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(idDto.getId(), dashboardDefinitionDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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

  private static Stream<List<DashboardFilterDto>> validFilterCombinations() {
    return Stream.of(
      null,
      Collections.emptyList(),
      Collections.singletonList(new DashboardFilterDto(DashboardFilterType.START_DATE, null)),
      Arrays.asList(
        new DashboardFilterDto(DashboardFilterType.START_DATE, null),
        new DashboardFilterDto(DashboardFilterType.END_DATE, null)
      ),
      Arrays.asList(
        new DashboardFilterDto(DashboardFilterType.START_DATE, null),
        new DashboardFilterDto(DashboardFilterType.END_DATE, null),
        new DashboardFilterDto(DashboardFilterType.STATE, null)
      ),
      Collections.singletonList(new DashboardFilterDto(
        DashboardFilterType.VARIABLE,
        new BooleanVariableFilterDataDto("boolVar", null)
      )),
      Collections.singletonList(new DashboardFilterDto(
        DashboardFilterType.VARIABLE,
        new DateVariableFilterDataDto("dateVar", null)
      )),
      Collections.singletonList(new DashboardFilterDto(
        DashboardFilterType.VARIABLE,
        new LongVariableFilterDataDto("longVar", "in", Arrays.asList("1", "2"))
      )),
      Collections.singletonList(new DashboardFilterDto(
        DashboardFilterType.VARIABLE,
        new ShortVariableFilterDataDto("shortVar", "in", Arrays.asList("1", "2"))
      )),
      Collections.singletonList(new DashboardFilterDto(
        DashboardFilterType.VARIABLE,
        new IntegerVariableFilterDataDto("integerVar", "in", Arrays.asList("1", "2"))
      )),
      Arrays.asList(
        new DashboardFilterDto(
          DashboardFilterType.VARIABLE,
          new BooleanVariableFilterDataDto("boolVar", null)
        ),
        new DashboardFilterDto(
          DashboardFilterType.VARIABLE,
          new LongVariableFilterDataDto("longVar", "in", Arrays.asList("1", "2"))
        ),
        new DashboardFilterDto(
          DashboardFilterType.VARIABLE,
          new DoubleVariableFilterDataDto("doubleVar", "in", Arrays.asList("1.0", "2.0"))
        ),
        new DashboardFilterDto(
          DashboardFilterType.VARIABLE,
          new StringVariableFilterDataDto("stringVar", "in", Arrays.asList("StringA", "StringB"))
        ),
        new DashboardFilterDto(DashboardFilterType.START_DATE, null),
        new DashboardFilterDto(DashboardFilterType.END_DATE, null),
        new DashboardFilterDto(DashboardFilterType.STATE, null)
      )
    );
  }

  private static Stream<List<DashboardFilterDto>> invalidFilterCombinations() {
    return Stream.of(
      Collections.singletonList(new DashboardFilterDto(null, null)),
      Collections.singletonList(new DashboardFilterDto(
        DashboardFilterType.START_DATE,
        new BooleanVariableFilterDataDto("boolVar", Collections.singletonList(true))
      )),
      Collections.singletonList(new DashboardFilterDto(
        DashboardFilterType.END_DATE,
        new BooleanVariableFilterDataDto("boolVar", Collections.singletonList(true))
      )),
      Collections.singletonList(new DashboardFilterDto(
        DashboardFilterType.STATE,
        new BooleanVariableFilterDataDto("boolVar", Collections.singletonList(true))
      )),
      Collections.singletonList(new DashboardFilterDto(
        DashboardFilterType.VARIABLE,
        null
      )),
      Arrays.asList(
        new DashboardFilterDto(DashboardFilterType.START_DATE, null),
        new DashboardFilterDto(DashboardFilterType.START_DATE, null)
      ),
      Arrays.asList(
        new DashboardFilterDto(DashboardFilterType.END_DATE, null),
        new DashboardFilterDto(DashboardFilterType.END_DATE, null)
      ),
      Arrays.asList(
        new DashboardFilterDto(DashboardFilterType.STATE, null),
        new DashboardFilterDto(DashboardFilterType.STATE, null)
      ),
      Collections.singletonList(new DashboardFilterDto(
        DashboardFilterType.VARIABLE,
        new DateVariableFilterDataDto("dateVar", new FixedDateFilterDataDto(null, OffsetDateTime.now()))
      ))
      ,
      Collections.singletonList(new DashboardFilterDto(
        DashboardFilterType.VARIABLE,
        new BooleanVariableFilterDataDto("boolVar", Collections.singletonList(true))
      ))
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
