/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.AGENTIC_DASHBOARD_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_COMPLETED_REPORT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import io.camunda.optimize.dto.optimize.rest.PublicAgenticDashboardEvaluationRequestDto;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class PublicApiAgenticDashboardIT extends AbstractCCSMIT {

  private static final String TEST_ACCESS_TOKEN = "test-access-token";

  @BeforeEach
  public void setUp() {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getOptimizeApiConfiguration()
        .setAccessToken(TEST_ACCESS_TOKEN);
    embeddedOptimizeExtension.getBean(AgenticControlDashboardService.class).reconcile();
  }

  @Test
  public void shouldReturnAgenticDashboardDefinitionWithValidToken() {
    // when
    final AuthorizedDashboardDefinitionResponseDto dashboard =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildPublicGetAgenticDashboardRequest()
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute(AuthorizedDashboardDefinitionResponseDto.class, HttpStatus.OK.value());

    // then the system-generated agentic dashboard is returned together with the report tile ids
    // that the hub can subsequently fetch via the public report result export endpoint
    assertThat(dashboard.getDefinitionDto().getId()).isEqualTo(AGENTIC_DASHBOARD_ID);
    assertThat(dashboard.getDefinitionDto().isAgenticControlDashboard()).isTrue();
    assertThat(dashboard.getDefinitionDto().getTiles())
        .isNotEmpty()
        .filteredOn(tile -> tile.getType() == DashboardTileType.OPTIMIZE_REPORT)
        .extracting(DashboardReportTileDto::getId)
        .doesNotContainNull();
  }

  @Test
  public void shouldReturn401WithoutToken() {
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildPublicGetAgenticDashboardRequest()
            .withoutAuthentication()
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  public void shouldReturn401EvaluatingAgenticReportWithoutUserIdentity() {
    // given a machine-to-machine static access token that carries no user subject
    final PublicAgenticDashboardEvaluationRequestDto request =
        new PublicAgenticDashboardEvaluationRequestDto();
    request.setProcessDefinitionKeys(List.of("some-process"));

    // when the agentic report tile is evaluated with only the static token
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildPublicEvaluateAgenticDashboardReportRequest(KPI_COMPLETED_REPORT_ID, request)
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();

    // then the request is rejected: the agentic report is evaluated with the identity of the
    // forwarded user, which the machine-to-machine static token does not provide
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  public void shouldReturn401EvaluatingAgenticReportWithoutToken() {
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildPublicEvaluateAgenticDashboardReportRequest(
                KPI_COMPLETED_REPORT_ID, new PublicAgenticDashboardEvaluationRequestDto())
            .withoutAuthentication()
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }
}
