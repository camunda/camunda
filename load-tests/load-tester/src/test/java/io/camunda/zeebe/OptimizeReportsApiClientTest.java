/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.config.OptimizeCfg;
import org.junit.jupiter.api.Test;

public class OptimizeReportsApiClientTest {

  private static OptimizeReportApiClient createTester() {
    final OptimizeCfg config = new OptimizeCfg();
    config.setBaseUrl("http://localhost:8083");
    config.setKeycloakUrl("http://localhost:18080");
    config.setRealm("camunda-platform");
    config.setClientId("optimize");
    config.setUsername("demo");
    config.setPassword("demo");
    config.setClientSecret("demo-secret");
    return new OptimizeReportApiClient(config);
  }

  @Test
  public void shouldExtractReportIdsFromDashboardResponse() throws Exception {
    // given
    final OptimizeReportApiClient tester = createTester();

    final String dashboardJson =
        """
        {
          "tiles": [
            {
              "id": "report-id-1",
              "position": {"x": 0, "y": 0},
              "dimensions": {"width": 4, "height": 2},
              "type": "optimize_report",
              "configuration": null
            },
            {
              "id": "report-id-2",
              "position": {"x": 4, "y": 0},
              "dimensions": {"width": 4, "height": 2},
              "type": "optimize_report",
              "configuration": null
            },
            {
              "id": "report-id-3",
              "position": {"x": 0, "y": 2},
              "dimensions": {"width": 4, "height": 2},
              "type": "optimize_report",
              "configuration": null
            }
          ]
        }
        """;

    // when
    final var reportIds = tester.extractReportIdsFromDashboard(dashboardJson);

    // then
    assertThat(reportIds).hasSize(3).containsExactly("report-id-1", "report-id-2", "report-id-3");
  }

  @Test
  public void shouldExtractReportIdsFromEmptyDashboard() throws Exception {
    // given
    final OptimizeReportApiClient tester = createTester();

    final String dashboardJson =
        """
        {
          "tiles": []
        }
        """;

    // when
    final var reportIds = tester.extractReportIdsFromDashboard(dashboardJson);

    // then
    assertThat(reportIds).isEmpty();
  }

  @Test
  public void shouldHandleMissingTilesArray() throws Exception {
    // given
    final OptimizeReportApiClient tester = createTester();

    final String dashboardJson = "{}";

    // when
    final var reportIds = tester.extractReportIdsFromDashboard(dashboardJson);

    // then
    assertThat(reportIds).isEmpty();
  }

  @Test
  public void shouldHandleTilesWithoutId() throws Exception {
    // given
    final OptimizeReportApiClient tester = createTester();

    final String dashboardJson =
        """
        {
          "tiles": [
            {
              "position": {"x": 0, "y": 0},
              "dimensions": {"width": 4, "height": 2},
              "type": "optimize_report"
            },
            {
              "id": "report-id-1",
              "position": {"x": 4, "y": 0},
              "dimensions": {"width": 4, "height": 2},
              "type": "optimize_report"
            }
          ]
        }
        """;

    // when
    final var reportIds = tester.extractReportIdsFromDashboard(dashboardJson);

    // then
    assertThat(reportIds).hasSize(1).containsExactly("report-id-1");
  }

  @Test
  public void shouldThrowExceptionForInvalidJson() {
    // given
    final OptimizeReportApiClient tester = createTester();

    final String invalidJson = "{ invalid json }";

    // when / then
    assertThatThrownBy(() -> tester.extractReportIdsFromDashboard(invalidJson))
        .isInstanceOf(Exception.class);
  }

  @Test
  public void shouldValidateDashboardEvaluationResult() {
    // given
    final var successResult =
        new OptimizeReportApiClient.DashboardEvaluationResult("management", 200, 1500, "{}");
    final var errorResult =
        new OptimizeReportApiClient.DashboardEvaluationResult("management", 500, 1000, "error");

    // then
    assertThat(successResult.isSuccess()).isTrue();
    assertThat(successResult.getDashboardType()).isEqualTo("management");
    assertThat(successResult.getStatusCode()).isEqualTo(200);
    assertThat(successResult.getResponseTimeMs()).isEqualTo(1500);

    assertThat(errorResult.isSuccess()).isFalse();
    assertThat(errorResult.getStatusCode()).isEqualTo(500);
  }

  @Test
  public void shouldValidateReportEvaluationResult() {
    // given
    final var successResult =
        new OptimizeReportApiClient.ReportEvaluationResult("report-1", 200, 2000, "{}");
    final var errorResult =
        new OptimizeReportApiClient.ReportEvaluationResult("report-2", 404, 500, "not found");

    // then
    assertThat(successResult.isSuccess()).isTrue();
    assertThat(successResult.getReportId()).isEqualTo("report-1");
    assertThat(successResult.getStatusCode()).isEqualTo(200);
    assertThat(successResult.getResponseTimeMs()).isEqualTo(2000);

    assertThat(errorResult.isSuccess()).isFalse();
    assertThat(errorResult.getStatusCode()).isEqualTo(404);
  }

  @Test
  public void shouldCalculateTotalResponseTimeForDashboardWithReports() {
    // given
    final var dashboardResult =
        new OptimizeReportApiClient.DashboardEvaluationResult("management", 200, 1000, "{}");
    final var report1Result =
        new OptimizeReportApiClient.ReportEvaluationResult("report-1", 200, 2000, "{}");
    final var report2Result =
        new OptimizeReportApiClient.ReportEvaluationResult("report-2", 200, 1500, "{}");

    final var result =
        new OptimizeReportApiClient.HomepageResult(
            dashboardResult, java.util.List.of(report1Result, report2Result));

    // then
    assertThat(result.getTotalResponseTimeMs()).isEqualTo(4500); // 1000 + 2000 + 1500
    assertThat(result.isAllSuccess()).isTrue();
    assertThat(result.getReportResults()).hasSize(2);
  }

  @Test
  public void shouldDetectFailureInDashboardWithReports() {
    // given
    final var dashboardResult =
        new OptimizeReportApiClient.DashboardEvaluationResult("management", 200, 1000, "{}");
    final var report1Result =
        new OptimizeReportApiClient.ReportEvaluationResult("report-1", 200, 2000, "{}");
    final var report2Result =
        new OptimizeReportApiClient.ReportEvaluationResult("report-2", 500, 1500, "error");

    final var result =
        new OptimizeReportApiClient.HomepageResult(
            dashboardResult, java.util.List.of(report1Result, report2Result));

    // then
    assertThat(result.isAllSuccess()).isFalse();
  }
}
