/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.dashboard.ManagementDashboardService;
import org.camunda.optimize.util.MarkdownUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DashboardUpdateRestServiceIT extends AbstractDashboardRestServiceIT {

  @Test
  public void updateDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildUpdateDashboardRequest("1", null)
      .execute();

    // then
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

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void updateManagementDashboardNotSupported() {
    // given
    embeddedOptimizeExtension.getManagementDashboardService().init();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(ManagementDashboardService.MANAGEMENT_DASHBOARD_ID, new DashboardDefinitionRestDto())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void updateDashboardRefreshRate() {
    // given
    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setRefreshRateSeconds(5L);
    final String dashboardId = dashboardClient.createDashboard(dashboard);
    assertThat(dashboardClient.getDashboard(dashboardId).getRefreshRateSeconds()).isEqualTo(5);
    dashboard.setRefreshRateSeconds(null);
    dashboard.setName("NEW NAME");

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboard)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(dashboardClient.getDashboard(dashboardId).getRefreshRateSeconds()).isNull();
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
    final DashboardReportTileDto variableReport = dashboardDefinitionDto.getTiles().get(0);
    String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // then
    assertThat(dashboardId).isNotNull();
    final DashboardDefinitionRestDto savedDefinition = dashboardClient.getDashboard(dashboardId);
    assertThat(savedDefinition.getAvailableFilters()).containsExactlyElementsOf(dashboardFilters);

    // when an external report is added to the dashboard
    DashboardReportTileDto externalReport = new DashboardReportTileDto();
    externalReport.setId("");
    externalReport.setType(DashboardTileType.EXTERNAL_URL);
    externalReport.setConfiguration(ImmutableMap.of("external", "https://www.tunnelsnakes.com/"));
    dashboardDefinitionDto.setTiles(Arrays.asList(externalReport, variableReport));

    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.NO_CONTENT.getStatusCode());

    // then
    assertThat(dashboardId).isNotNull();
    final DashboardDefinitionRestDto updatedDefinition = dashboardClient.getDashboard(dashboardId);
    assertThat(updatedDefinition.getAvailableFilters()).containsExactlyElementsOf(dashboardFilters);
    assertThat(updatedDefinition.getTiles())
      .containsExactlyInAnyOrder(externalReport, variableReport);
  }

  @Test
  public void updateDashboard_addTextReportToDashboard() {
    // given
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardForReportContainingAllVariables(Collections.emptyList());
    final DashboardReportTileDto variableReport = dashboardDefinitionDto.getTiles().get(0);
    String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // then
    assertThat(dashboardId).isNotNull();

    // when a text report is added to the dashboard
    DashboardReportTileDto textReport = new DashboardReportTileDto();
    textReport.setId("");
    textReport.setType(DashboardTileType.TEXT);
    textReport.setConfiguration(MarkdownUtil.getMarkdownForTextReport("I _love_ *markdown*."));
    // adding also an empty text report to the dashboard
    DashboardReportTileDto emptyTextReport = new DashboardReportTileDto();
    emptyTextReport.setId("");
    emptyTextReport.setType(DashboardTileType.TEXT);
    emptyTextReport.setConfiguration(MarkdownUtil.getMarkdownForTextReport(""));
    dashboardDefinitionDto.setTiles(Arrays.asList(textReport, variableReport, emptyTextReport));

    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.NO_CONTENT.getStatusCode());

    // then
    assertThat(dashboardId).isNotNull();
    final DashboardDefinitionRestDto updatedDefinition = dashboardClient.getDashboard(dashboardId);
    assertThat(updatedDefinition.getTiles())
      .containsExactlyInAnyOrder(textReport, variableReport, emptyTextReport);
  }

  @Test
  public void updateDashboard_updateExistingTextReport() {
    // given
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardForReportContainingAllVariables(Collections.emptyList());
    final DashboardReportTileDto variableReport = dashboardDefinitionDto.getTiles().get(0);
    String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // then
    assertThat(dashboardId).isNotNull();

    // when a text report is added to the dashboard
    DashboardReportTileDto textReport = new DashboardReportTileDto();
    textReport.setId("");
    textReport.setType(DashboardTileType.TEXT);
    textReport.setConfiguration(MarkdownUtil.getMarkdownForTextReport("I _love_ *markdown*."));
    dashboardDefinitionDto.setTiles(Arrays.asList(textReport, variableReport));

    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.NO_CONTENT.getStatusCode());

    // then
    assertThat(dashboardId).isNotNull();
    final DashboardDefinitionRestDto updatedDefinition = dashboardClient.getDashboard(dashboardId);
    assertThat(updatedDefinition.getTiles())
      .containsExactlyInAnyOrder(textReport, variableReport);

    // when the text report is updated
    DashboardReportTileDto updatedTextReport = new DashboardReportTileDto();
    updatedTextReport.setId("");
    updatedTextReport.setType(DashboardTileType.TEXT);
    updatedTextReport.setConfiguration(MarkdownUtil.getMarkdownForTextReport("Updated text"));
    dashboardDefinitionDto.setTiles(Arrays.asList(updatedTextReport, variableReport));

    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.NO_CONTENT.getStatusCode());

    // then
    final DashboardDefinitionRestDto newlyUpdatedDefinition = dashboardClient.getDashboard(dashboardId);
    assertThat(newlyUpdatedDefinition.getTiles())
      .containsExactlyInAnyOrder(updatedTextReport, variableReport);
  }

  @ParameterizedTest
  @MethodSource("getInvalidReportIdAndTypes")
  public void updateDashboard_invalidIdAndTypeCombinations(final DashboardReportTileDto dashboardReportTileDto) {
    // given
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardForReportContainingAllVariables(Collections.emptyList());
    final DashboardReportTileDto variableReport = dashboardDefinitionDto.getTiles().get(0);
    String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // then
    assertThat(dashboardId).isNotNull();

    // when an invalid tile is added to dashboard
    dashboardDefinitionDto.setTiles(Arrays.asList(dashboardReportTileDto, variableReport));
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinitionDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateDashboard_dashboardTileDoesNotSpecifyType() {
    // given
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardForReportContainingAllVariables(Collections.emptyList());
    String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // then
    assertThat(dashboardId).isNotNull();

    // when an external report without a type specified is added to the dashboard
    DashboardReportTileDto externalReport = new DashboardReportTileDto();
    externalReport.setId("");
    externalReport.setConfiguration(ImmutableMap.of("external", "https://www.tunnelsnakes.com/"));
    dashboardDefinitionDto.setTiles(List.of(externalReport));

    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinitionDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    // This string was reported as a vulnerability in https://jira.camunda.com/browse/OPT-6583
    "javascript:alert(\"SySS Stored XSS Proof of Concept within domain:\\n\\n\"+document.domain)",
    "invalidExternalUrl.com"
  })
  public void updateDashboard_dashboardContainsExternalReportWithInvalidURL(final String url) {
    // given
    String dashboardId = dashboardClient.createEmptyDashboard();

    // when an external report is added to the dashboard
    final DashboardDefinitionRestDto dashboardDefinitionDto = dashboardClient.getDashboard(dashboardId);
    DashboardReportTileDto externalReport = new DashboardReportTileDto();
    externalReport.setId("");
    externalReport.setConfiguration(ImmutableMap.of("external", url));
    dashboardDefinitionDto.setTiles(List.of(externalReport));

    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinitionDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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

}
