/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.dashboard.ManagementDashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
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

}
