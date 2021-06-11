/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardAssigneeFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardCandidateGroupFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardStateFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardBooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDoubleVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardIdentityFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardIntegerVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardLongVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardShortVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardStateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardStringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
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
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.CONTAINS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_CONTAINS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.rest.RestTestUtil.getOffsetDiffInHours;
import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_SHARE_INDEX_NAME;
import static org.mockserver.model.HttpRequest.request;

public class DashboardRestServiceIT extends AbstractIT {

  private static final ImmutableMap<String, Object> ALL_VARIABLES = ImmutableMap.<String, Object>builder()
    .put("boolVar", true)
    .put("dateVar", OffsetDateTime.now())
    .put("longVar", 1L)
    .put("shortVar", (short) 2)
    .put("integerVar", 3)
    .put("doubleVar", 4.0D)
    .put("stringVar", "sillyString")
    .build();

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
    IdResponseDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(idDto).isNotNull();
  }

  @Test
  public void createNewDashboardWithDefinition() {
    // when
    IdResponseDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(generateDashboardDefinitionDto())
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(idDto).isNotNull();
  }

  @ParameterizedTest
  @MethodSource("validFilterCombinations")
  public void createNewDashboardWithFilterSpecification(List<DashboardFilterDto<?>> dashboardFilterDtos) {
    // given
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardForReportContainingAllVariables(dashboardFilterDtos);

    // when
    final IdResponseDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(idDto.getId()).isNotNull();
    final DashboardDefinitionRestDto savedDefinition = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDashboardRequest(idDto.getId())
      .execute(DashboardDefinitionRestDto.class, Response.Status.OK.getStatusCode());
    if (dashboardFilterDtos == null) {
      assertThat(savedDefinition.getAvailableFilters()).isNull();
    } else {
      assertThat(savedDefinition.getAvailableFilters()).containsExactlyInAnyOrderElementsOf(dashboardFilterDtos);
    }
  }

  @ParameterizedTest
  @MethodSource("invalidFilterCombinations")
  public void createNewDashboardWithInvalidFilterSpecification(List<DashboardFilterDto<?>> dashboardFilterDtos) {
    // given
    final DashboardDefinitionRestDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    dashboardDefinitionDto.setAvailableFilters(dashboardFilterDtos);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createNewDashboardWithFilterSpecification_dashboardContainsExternalReport() {
    // given
    final List<DashboardFilterDto<?>> dashboardFilters = variableFilter();
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardForReportContainingAllVariables(dashboardFilters);
    final ReportLocationDto variableReport = dashboardDefinitionDto.getReports().get(0);

    ReportLocationDto externalReport = new ReportLocationDto();
    externalReport.setId("");
    externalReport.setConfiguration(ImmutableMap.of("external", "https://www.tunnelsnakes.com/"));
    dashboardDefinitionDto.setReports(Arrays.asList(externalReport, variableReport));

    // when
    final IdResponseDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(idDto.getId()).isNotNull();
    final DashboardDefinitionRestDto savedDefinition = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDashboardRequest(idDto.getId())
      .execute(DashboardDefinitionRestDto.class, Response.Status.OK.getStatusCode());
    assertThat(savedDefinition.getReports())
      .containsExactlyInAnyOrder(variableReport, externalReport);
    assertThat(savedDefinition.getAvailableFilters()).containsExactlyInAnyOrderElementsOf(dashboardFilters);
  }

  @Test
  public void createNewDashboardWithVariableFilter_variableNameNotInContainedReport() {
    // given
    final List<DashboardFilterDto<?>> variableFilter = variableFilter();
    final DashboardDefinitionRestDto dashboardDefinitionDto = new DashboardDefinitionRestDto();
    dashboardDefinitionDto.setAvailableFilters(variableFilter);

    // when
    final ErrorResponseDto errorResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then the response has the expected error code
    assertThat(errorResponse.getErrorCode()).isEqualTo("invalidDashboardVariableFilter");
  }

  @Test
  public void createNewDashboardWithVariableFilter_variableValueNotInContainedReport() {
    // given
    final DashboardFilterDto<?> dashboardFilter = createDashboardVariableFilter(
      VariableType.STRING, "stringVar", IN,
      Collections.singletonList("thisValueIsNotInReport"), false
    );
    final List<DashboardFilterDto<?>> dashboardFilters = Collections.singletonList(dashboardFilter);

    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardForReportContainingAllVariables(dashboardFilters);

    // when
    final IdResponseDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(idDto.getId()).isNotNull();
    final DashboardDefinitionRestDto savedDefinition = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDashboardRequest(idDto.getId())
      .execute(DashboardDefinitionRestDto.class, Response.Status.OK.getStatusCode());
    assertThat(savedDefinition.getAvailableFilters()).containsExactlyInAnyOrderElementsOf(dashboardFilters);
  }

  @Test
  public void copyPrivateDashboard() {
    // given
    String dashboardId = dashboardClient.createEmptyDashboard(null);
    createEmptyReportToDashboard(dashboardId);

    // when
    IdResponseDto copyId = dashboardClient.copyDashboard(dashboardId);

    // then
    DashboardDefinitionRestDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard).hasToString(oldDashboard.toString());
    assertThat(dashboard.getName()).isEqualTo(oldDashboard.getName() + " – Copy");

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    final List<String> oldDashboardReportIds = oldDashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());
    assertThat(newReportIds)
      .isNotEmpty()
      .containsExactlyInAnyOrderElementsOf(oldDashboardReportIds);
  }

  @Test
  public void copyPrivateDashboardWithNameParameter() {
    // given
    final String dashboardId = dashboardClient.createEmptyDashboard(null);
    createEmptyReportToDashboard(dashboardId);

    final String testDashboardCopyName = "This is my new report copy! ;-)";

    // when
    IdResponseDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("name", testDashboardCopyName)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    DashboardDefinitionRestDto oldDashboard = dashboardClient.getDashboard(dashboardId);
    DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(copyId.getId());
    assertThat(dashboard).hasToString(oldDashboard.toString());
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
    // given
    DashboardDefinitionRestDto definitionDto = generateDashboardDefinitionDto();
    String id = dashboardClient.createDashboard(generateDashboardDefinitionDto());

    // when
    DashboardDefinitionRestDto returnedDashboard = dashboardClient.getDashboard(id);

    // then
    assertThat(returnedDashboard).isNotNull();
    assertThat(returnedDashboard.getId()).isEqualTo(id);
    assertThat(returnedDashboard.getName()).isEqualTo(definitionDto.getName());
    assertThat(returnedDashboard.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(returnedDashboard.getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
  }

  @Test
  public void getDashboard_adoptTimezoneFromHeader() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    generateDashboardDefinitionDto();
    String dashboardId = dashboardClient.createDashboard(generateDashboardDefinitionDto());

    // when
    DashboardDefinitionRestDto returnedDashboard = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDashboardRequest(dashboardId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      .execute(DashboardDefinitionRestDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(returnedDashboard).isNotNull();
    assertThat(returnedDashboard.getCreated()).isEqualTo(now);
    assertThat(returnedDashboard.getLastModified()).isEqualTo(now);
    assertThat(getOffsetDiffInHours(returnedDashboard.getCreated(), now)).isEqualTo(1.);
    assertThat(getOffsetDiffInHours(returnedDashboard.getLastModified(), now)).isEqualTo(1.);
  }

  @Test
  public void getDashboardForNonExistingIdThrowsError() {
    // when
    String response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDashboardRequest("fooid")
      .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());

    // then the status code is okay
    assertThat(response).containsSequence("Dashboard does not exist!");
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
      .buildUpdateDashboardRequest("nonExistingId", new DashboardDefinitionRestDto())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void updateDashboard() {
    // given
    String id = dashboardClient.createEmptyDashboard(null);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, new DashboardDefinitionRestDto())
      .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("validFilterCombinations")
  public void updateDashboardFilterSpecification(List<DashboardFilterDto<?>> dashboardFilterDtos) {
    // given
    final DashboardDefinitionRestDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // then
    assertThat(dashboardId).isNotNull();
    final DashboardDefinitionRestDto savedDefinition = dashboardClient.getDashboard(dashboardId);
    assertThat(savedDefinition.getAvailableFilters()).isEmpty();

    // when
    final DashboardDefinitionRestDto dashboardUpdate =
      createDashboardForReportContainingAllVariables(dashboardFilterDtos);
    dashboardUpdate.setId(dashboardId);
    dashboardDefinitionDto.setAvailableFilters(dashboardFilterDtos);
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardUpdate)
      .execute();
    final DashboardDefinitionRestDto updatedDefinition = dashboardClient.getDashboard(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(updatedDefinition.getId()).isEqualTo(savedDefinition.getId());
    if (dashboardFilterDtos == null) {
      assertThat(updatedDefinition.getAvailableFilters()).isEmpty();
    } else {
      assertThat(updatedDefinition.getAvailableFilters()).containsExactlyInAnyOrderElementsOf(dashboardFilterDtos);
    }
  }

  @Test
  public void updateDashboardWithFilterSpecification_dashboardContainsExternalReport() {
    // given a dashboard with a variable filter
    final List<DashboardFilterDto<?>> dashboardFilters = variableFilter();
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardForReportContainingAllVariables(dashboardFilters);
    final ReportLocationDto variableReport = dashboardDefinitionDto.getReports().get(0);
    String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // then
    assertThat(dashboardId).isNotNull();
    final DashboardDefinitionRestDto savedDefinition = dashboardClient.getDashboard(dashboardId);
    assertThat(savedDefinition.getAvailableFilters()).containsExactlyElementsOf(dashboardFilters);

    // when an external report is added to the dashboard
    ReportLocationDto externalReport = new ReportLocationDto();
    externalReport.setId("");
    externalReport.setConfiguration(ImmutableMap.of("external", "https://www.tunnelsnakes.com/"));
    dashboardDefinitionDto.setReports(Arrays.asList(externalReport, variableReport));

    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.NO_CONTENT.getStatusCode());

    // then
    assertThat(dashboardId).isNotNull();
    final DashboardDefinitionRestDto updatedDefinition = dashboardClient.getDashboard(dashboardId);
    assertThat(updatedDefinition.getAvailableFilters()).containsExactlyElementsOf(dashboardFilters);
    assertThat(updatedDefinition.getReports())
      .containsExactlyInAnyOrder(externalReport, variableReport);
  }

  @Test
  public void updateDashboardWithVariableFilter_variableNotInContainedReport() {
    // given
    final DashboardDefinitionRestDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // then
    assertThat(dashboardId).isNotNull();
    final DashboardDefinitionRestDto savedDefinition = dashboardClient.getDashboard(dashboardId);
    assertThat(savedDefinition.getAvailableFilters()).isEmpty();

    // when
    dashboardDefinitionDto.setAvailableFilters(variableFilter());
    final ErrorResponseDto errorResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinitionDto)
      .execute(ErrorResponseDto.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then the response has the expected error code
    assertThat(errorResponse.getErrorCode()).isEqualTo("invalidDashboardVariableFilter");
  }

  @ParameterizedTest
  @MethodSource("invalidFilterCombinations")
  public void updateDashboardFilterSpecification_invalidFilters(List<DashboardFilterDto<?>> dashboardFilterDtos) {
    // when
    final DashboardDefinitionRestDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    IdResponseDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(idDto.getId()).isNotNull();
    final DashboardDefinitionRestDto savedDefinition = dashboardClient.getDashboard(idDto.getId());
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
    // given
    final String collectionId = collectionClient.createNewCollection();
    DashboardDefinitionRestDto dashboardDefinitionDto = new DashboardDefinitionRestDto();
    dashboardDefinitionDto.setCollectionId(collectionId);
    String id = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when
    dashboardClient.updateDashboard(id, new DashboardDefinitionRestDto());

    // then
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(id);
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
    // given
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
                           .readValue(searchHitFields.getSourceAsString(), DashboardShareRestDto.class).getId());
    }
    return storedShareIds.contains(shareId);
  }

  private static Stream<List<DashboardFilterDto<?>>> validFilterCombinations() {
    return Stream.of(
      null,
      Collections.emptyList(),
      Collections.singletonList(createDashboardStartDateFilterWithDefaultValues(null)),
      Arrays.asList(
        createDashboardStartDateFilterWithDefaultValues(null),
        createDashboardEndDateFilterWithDefaultValues(null)
      ),
      Arrays.asList(
        createDashboardStartDateFilterWithDefaultValues(null),
        createDashboardEndDateFilterWithDefaultValues(null),
        createDashboardStateFilterWithDefaultValues(null)
      ),
      Collections.singletonList(createDashboardVariableFilter(VariableType.BOOLEAN, "boolVar")),
      Collections.singletonList(createDashboardVariableFilter(VariableType.DATE, "dateVar")),
      variableFilter(),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.LONG, "longVar", IN, Arrays.asList("1", "2"), false)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.SHORT, "shortVar", IN, Arrays.asList("1", "2"), false)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.INTEGER, "integerVar", IN, Arrays.asList("1", "2"), false)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.INTEGER, "integerVar", IN, Arrays.asList("1", "2"), true)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.LONG, "longVar", IN, Arrays.asList("1", "2"), true)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.SHORT, "shortVar", IN, Arrays.asList("1", "2"), true)),
      Arrays.asList(
        createDashboardVariableFilter(VariableType.LONG, "longVar", IN, Arrays.asList("1", "2"), false),
        createDashboardVariableFilter(VariableType.DOUBLE, "doubleVar", IN, Arrays.asList("1", "2"), true)
      ),
      Arrays.asList(
        createDashboardAssigneeFilter(Collections.emptyList(), IN, false),
        createDashboardCandidateGroupFilter(Collections.emptyList(), NOT_IN, true)
      ),
      Arrays.asList(
        createDashboardAssigneeFilter(Arrays.asList("Rose", "Martha"), IN, false),
        createDashboardAssigneeFilter(Arrays.asList("Donna", "Clara"), NOT_IN, true)
      ),
      Arrays.asList(
        createDashboardCandidateGroupFilter(Arrays.asList("Cybermen", "Daleks"), IN, true),
        createDashboardCandidateGroupFilter(Arrays.asList("Ood", "Judoon"), NOT_IN, false)
      ),
      Arrays.asList(
        createDashboardAssigneeFilter(Arrays.asList("Rose", "Martha"), IN, true),
        createDashboardCandidateGroupFilter(Arrays.asList("Ood", "Judoon"), NOT_IN, true)
      ),
      Arrays.asList(
        createDashboardVariableFilter(VariableType.BOOLEAN, "boolVar"),
        createDashboardVariableFilter(VariableType.DATE, "dateVar"),
        createDashboardVariableFilter(VariableType.LONG, "longVar", IN, Arrays.asList("1", "2"), false),
        createDashboardVariableFilter(VariableType.DOUBLE, "doubleVar", IN, Arrays.asList("1.0", "2.0"), false),
        createDashboardVariableFilter(VariableType.STRING, "stringVar", IN, Arrays.asList("StringA", "StringB"),
                                      false
        ),
        createDashboardVariableFilter(
          VariableType.STRING,
          "stringVar",
          CONTAINS,
          Arrays.asList("StringA", "StringB"),
          false
        ),
        createDashboardVariableFilter(
          VariableType.STRING,
          "stringVar",
          NOT_CONTAINS,
          Collections.singletonList("foo"),
          false
        ),
        createDashboardAssigneeFilter(Arrays.asList("Rose", "Martha"), IN, false),
        createDashboardCandidateGroupFilter(Arrays.asList("Cybermen", "Daleks"), IN, false),
        createDashboardStartDateFilterWithDefaultValues(null),
        createDashboardEndDateFilterWithDefaultValues(null),
        createDashboardStateFilterWithDefaultValues(null)
      ),
      Arrays.asList(
        createDashboardVariableFilter(VariableType.BOOLEAN, "boolVar"),
        createDashboardVariableFilter(VariableType.DATE, "dateVar"),
        createDashboardVariableFilter(VariableType.LONG, "longVar", IN, Arrays.asList("1", "2"), true),
        createDashboardVariableFilter(VariableType.DOUBLE, "doubleVar", IN, Arrays.asList("1.0", "2.0"), true),
        createDashboardVariableFilter(VariableType.STRING, "stringVar", IN, Arrays.asList("StringA", "StringB"), true),
        createDashboardVariableFilter(
          VariableType.STRING,
          "stringVar",
          CONTAINS,
          Arrays.asList("StringA", "StringB"),
          false
        ),
        createDashboardVariableFilter(
          VariableType.STRING,
          "stringVar",
          NOT_CONTAINS,
          Collections.singletonList("foo"),
          false
        ),
        createDashboardAssigneeFilter(Arrays.asList("Rose", "Martha"), IN, true),
        createDashboardCandidateGroupFilter(Arrays.asList("Cybermen", "Daleks"), IN, true),
        createDashboardStartDateFilterWithDefaultValues(null),
        createDashboardEndDateFilterWithDefaultValues(null),
        createDashboardStateFilterWithDefaultValues(null)
      ),
      Arrays.asList(
        createDashboardDateVariableFilterWithDefaultValues(
          new FixedDateFilterDataDto(
            OffsetDateTime.parse("2021-06-07T18:00:00+02:00"),
            OffsetDateTime.parse("2021-06-08T18:00:00+02:00")
          )
        ),
        createDashboardDateVariableFilterWithDefaultValues(
          new RollingDateFilterDataDto(new RollingDateFilterStartDto(1L, DateFilterUnit.YEARS))
        ),
        createDashboardDateVariableFilterWithDefaultValues(
          new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(2L, DateFilterUnit.MINUTES))
        ),
        createDashboardBooleanVariableFilterWithDefaultValues(),
        createDashboardStringVariableFilterWithDefaultValues(),
        createDashboardShortVariableFilterWithDefaultValues(),
        createDashboardLongVariableFilterWithDefaultValues(),
        createDashboardDoubleVariableFilterWithDefaultValues(),
        createDashboardIntegerVariableFilterWithDefaultValues(),
        createDashboardStateFilterWithDefaultValues(Collections.singletonList("canceledInstancesOnly")),
        createDashboardStartDateFilterWithDefaultValues(
          new FixedDateFilterDataDto(
            OffsetDateTime.parse("2021-06-07T18:00:00+02:00"),
            OffsetDateTime.parse("2021-06-08T18:00:00+02:00")
          )),
        createDashboardEndDateFilterWithDefaultValues(
          new FixedDateFilterDataDto(
            OffsetDateTime.parse("2021-06-05T18:00:00+02:00"),
            OffsetDateTime.parse("2021-06-06T18:00:00+02:00")
          )),
        createDashboardAssigneeFilter(Arrays.asList("Rose", "Martha"), IN, true, Collections.singletonList("Martha")),
        createDashboardCandidateGroupFilter(
          Arrays.asList("Cybermen", "Daleks"),
          NOT_IN,
          true,
          Arrays.asList("Cybermen", "Daleks")
        )
      ),
      Collections.singletonList(
        createDashboardEndDateFilterWithDefaultValues(
          new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(1L, DateFilterUnit.MINUTES))
        )
      ),
      Collections.singletonList(
        createDashboardEndDateFilterWithDefaultValues(
          new RollingDateFilterDataDto(new RollingDateFilterStartDto(2L, DateFilterUnit.YEARS))
        )
      ),
      Collections.singletonList(
        createDashboardStartDateFilterWithDefaultValues(
          new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(3L, DateFilterUnit.SECONDS))
        )
      ),
      Collections.singletonList(
        createDashboardStartDateFilterWithDefaultValues(
          new RollingDateFilterDataDto(new RollingDateFilterStartDto(4L, DateFilterUnit.DAYS))
        )
      )
    );
  }

  private static Stream<List<DashboardFilterDto>> invalidFilterCombinations() {
    return Stream.of(
      Collections.singletonList(new DashboardVariableFilterDto()),
      Collections.singletonList(new DashboardAssigneeFilterDto()),
      Collections.singletonList(new DashboardCandidateGroupFilterDto()),
      Collections.singletonList(createDashboardAssigneeFilter(Collections.emptyList(), CONTAINS, false)),
      Arrays.asList(
        createDashboardAssigneeFilter(Collections.emptyList(), IN, false),
        createDashboardAssigneeFilter(Collections.emptyList(), IN, true)
      ),
      Arrays.asList(
        createDashboardCandidateGroupFilter(Collections.emptyList(), NOT_IN, false),
        createDashboardCandidateGroupFilter(Collections.emptyList(), NOT_IN, true)
      ),
      Arrays.asList(
        new DashboardStartDateFilterDto(),
        new DashboardStartDateFilterDto()
      ),
      Arrays.asList(
        new DashboardEndDateFilterDto(),
        new DashboardEndDateFilterDto()
      ),
      Arrays.asList(
        new DashboardStateFilterDto(),
        new DashboardStateFilterDto()
      ),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.DATE, "dateVar", IN, Collections.singletonList(OffsetDateTime.now().toString()), false)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.DATE, "dateVar", IN, Collections.singletonList(OffsetDateTime.now().toString()), true)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.BOOLEAN, "boolVar", IN, Collections.singletonList("true"), false)),
      Collections.singletonList(createDashboardVariableFilter(
        VariableType.BOOLEAN, "boolVar", IN, Collections.singletonList("true"), true)),
      Collections.singletonList(createDashboardVariableFilter(VariableType.LONG, "longVar", null)),
      Collections.singletonList(createDashboardVariableFilter(VariableType.DOUBLE, "doubleVar", null)),
      Collections.singletonList(createDashboardVariableFilter(VariableType.STRING, "stringVar", null))
    );
  }

  private void createEmptyReportToDashboard(final String dashboardId) {
    final String reportId = reportClient.createEmptySingleProcessReportInCollection(null);
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportId));
  }

  private DashboardDefinitionRestDto generateDashboardDefinitionDto() {
    DashboardDefinitionRestDto dashboardDefinitionDto = new DashboardDefinitionRestDto();
    dashboardDefinitionDto.setName("Dashboard name");
    return dashboardDefinitionDto;
  }

  private DashboardDefinitionRestDto createDashboardForReportContainingAllVariables(
    final List<DashboardFilterDto<?>> dashboardFilterDtos) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("someProcess").startEvent().endEvent().done();
    final ProcessInstanceEngineDto deployedInstanceWithAllVariables =
      engineIntegrationExtension.deployAndStartProcessWithVariables(
        modelInstance,
        ALL_VARIABLES
      );
    importAllEngineEntitiesFromScratch();
    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      reportClient.createSingleProcessReportDefinitionDto(
        null,
        deployedInstanceWithAllVariables.getProcessDefinitionKey(),
        Collections.singletonList(null)
      );
    singleProcessReportDefinitionDto.getData()
      .setProcessDefinitionVersion(deployedInstanceWithAllVariables.getProcessDefinitionVersion());
    final String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
    final DashboardDefinitionRestDto dashboardDefinitionDto = generateDashboardDefinitionDto();
    dashboardDefinitionDto.setReports(Collections.singletonList(ReportLocationDto.builder().id(reportId).build()));
    dashboardDefinitionDto.setAvailableFilters(dashboardFilterDtos);
    return dashboardDefinitionDto;
  }

  private static DashboardFilterDto<?> createDashboardVariableFilter(final VariableType type,
                                                                     final String variableName) {
    return createDashboardVariableFilter(type, variableName, null);
  }

  private static DashboardFilterDto<?> createDashboardVariableFilter(final VariableType type,
                                                                     final String variableName,
                                                                     final FilterOperator operator,
                                                                     final List<String> values,
                                                                     final boolean allowCustomValues) {
    switch (type) {
      case LONG:
      case SHORT:
      case DOUBLE:
      case STRING:
      case INTEGER:
        return createDashboardVariableFilter(
          type,
          variableName,
          new DashboardVariableFilterSubDataDto(operator, values, allowCustomValues)
        );
      case DATE:
      case BOOLEAN:
        return createDashboardVariableFilter(type, variableName, null);
      default:
        throw new OptimizeIntegrationTestException("Unknown variable type: " + type);
    }
  }

  private static DashboardFilterDto<?> createDashboardVariableFilter(final VariableType type,
                                                                     final String variableName,
                                                                     final DashboardVariableFilterSubDataDto subData) {
    final DashboardVariableFilterDto variableFilter = new DashboardVariableFilterDto();
    DashboardVariableFilterDataDto filterData;
    switch (type) {
      case DATE:
        filterData = new DashboardDateVariableFilterDataDto(variableName);
        break;
      case LONG:
        filterData = new DashboardLongVariableFilterDataDto(variableName, subData);
        break;
      case SHORT:
        filterData = new DashboardShortVariableFilterDataDto(variableName, subData);
        break;
      case DOUBLE:
        filterData = new DashboardDoubleVariableFilterDataDto(variableName, subData);
        break;
      case STRING:
        filterData = new DashboardStringVariableFilterDataDto(variableName, subData);
        break;
      case BOOLEAN:
        filterData = new DashboardBooleanVariableFilterDataDto(variableName);
        break;
      case INTEGER:
        filterData = new DashboardIntegerVariableFilterDataDto(variableName, subData);
        break;
      default:
        throw new OptimizeIntegrationTestException("Unknown variable type: " + type);
    }
    variableFilter.setData(filterData);
    return variableFilter;
  }

  private static DashboardFilterDto<?> createDashboardDateVariableFilterWithDefaultValues(final DateFilterDataDto<?> defaultValues) {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(VariableType.DATE, "dateVar", null);
    ((DashboardDateVariableFilterDataDto) filterDto.getData()).setDefaultValues(defaultValues);
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardBooleanVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(VariableType.BOOLEAN, "boolVar", null);
    ((DashboardBooleanVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList(true));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardStringVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(
        VariableType.STRING,
        "stringVar",
        IN,
        List.of("aStringValue", "anotherStringValue"),
        false
      );
    ((DashboardStringVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList(
      "aStringValue"));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardIntegerVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(
        VariableType.INTEGER,
        "integerVar",
        NOT_IN,
        List.of("7", "8"),
        false
      );
    ((DashboardIntegerVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList("8"));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardShortVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(
        VariableType.SHORT,
        "shortVar",
        IN,
        List.of("1", "2"),
        false
      );
    ((DashboardShortVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList("1"));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardLongVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(
        VariableType.LONG,
        "longVar",
        IN,
        List.of("3", "4"),
        false
      );
    ((DashboardLongVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList("4"));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardDoubleVariableFilterWithDefaultValues() {
    DashboardVariableFilterDto filterDto =
      (DashboardVariableFilterDto) createDashboardVariableFilter(
        VariableType.DOUBLE,
        "doubleVar",
        NOT_IN,
        List.of("5.0", "6.0"),
        false
      );
    ((DashboardDoubleVariableFilterDataDto) filterDto.getData()).setDefaultValues(Collections.singletonList("5.0"));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardStartDateFilterWithDefaultValues(final DateFilterDataDto<?> defaultValues) {
    DashboardStartDateFilterDto filterDto = new DashboardStartDateFilterDto();
    filterDto.setData(new DashboardDateFilterDataDto(defaultValues));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardEndDateFilterWithDefaultValues(final DateFilterDataDto<?> defaultValues) {
    DashboardEndDateFilterDto filterDto = new DashboardEndDateFilterDto();
    filterDto.setData(new DashboardDateFilterDataDto(defaultValues));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardStateFilterWithDefaultValues(final List<String> defaultValues) {
    DashboardStateFilterDto filterDto = new DashboardStateFilterDto();
    filterDto.setData(new DashboardStateFilterDataDto(defaultValues));
    return filterDto;
  }

  private static DashboardFilterDto<?> createDashboardAssigneeFilter(final List<String> assigneeNames,
                                                                     final FilterOperator filterOperator,
                                                                     final boolean allowCustomValues) {
    return createDashboardAssigneeFilter(assigneeNames, filterOperator, allowCustomValues, null);
  }

  private static DashboardFilterDto<?> createDashboardAssigneeFilter(final List<String> assigneeNames,
                                                                     final FilterOperator filterOperator,
                                                                     final boolean allowCustomValues,
                                                                     final List<String> defaultValues) {
    final DashboardAssigneeFilterDto assigneeFilter = new DashboardAssigneeFilterDto();
    assigneeFilter.setData(new DashboardIdentityFilterDataDto(
      filterOperator,
      assigneeNames,
      allowCustomValues,
      defaultValues
    ));
    return assigneeFilter;
  }

  private static DashboardFilterDto<?> createDashboardCandidateGroupFilter(final List<String> candidateGroupNames,
                                                                           final FilterOperator filterOperator,
                                                                           final boolean allowCustomValues) {
    return createDashboardCandidateGroupFilter(candidateGroupNames, filterOperator, allowCustomValues, null);
  }

  private static DashboardFilterDto<?> createDashboardCandidateGroupFilter(final List<String> candidateGroupNames,
                                                                           final FilterOperator filterOperator,
                                                                           final boolean allowCustomValues,
                                                                           final List<String> defaultValues) {
    final DashboardCandidateGroupFilterDto candidateGroupFilter = new DashboardCandidateGroupFilterDto();
    candidateGroupFilter.setData(new DashboardIdentityFilterDataDto(
      filterOperator,
      candidateGroupNames,
      allowCustomValues,
      defaultValues
    ));
    return candidateGroupFilter;
  }

  private static List<DashboardFilterDto<?>> variableFilter() {
    final DashboardVariableFilterDto variableFilter = new DashboardVariableFilterDto();
    variableFilter.setData(new DashboardDateVariableFilterDataDto("dateVar"));
    return Collections.singletonList(variableFilter);
  }
}
