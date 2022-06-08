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
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;

public class DashboardCreateRestServiceIT extends AbstractDashboardRestServiceIT {

  @Test
  public void createNewDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildCreateDashboardRequest(generateDashboardDefinitionDto())
      .execute();

    // then
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

  @Test
  public void createNewManagementDashboardNotSupported() {
    // given
    final DashboardDefinitionRestDto dashboardDefinition = generateDashboardDefinitionDto();
    dashboardDefinition.setManagementDashboard(true);

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinition)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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

    // then
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
  public void createDashboardWithRefreshRate() {
    // given
    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setRefreshRateSeconds(5L);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboard)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

}
