/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class InstantPreviewTemplate4Test {

  private static final List<String> EXPECTED_AGENTIC_REPORT_IDS =
      List.of(
          "agentic-total-runs",
          "agentic-duration-summary",
          "agentic-duration-trend",
          "agentic-incident-count",
          "agentic-incident-rate",
          "agentic-incident-rate-by-version",
          "agentic-tokens-summary",
          "agentic-tokens-trend",
          "agentic-tokens-input-trend",
          "agentic-tokens-output-trend",
          "agentic-tokens-by-process",
          "agentic-process-instance-count-by-process",
          "agentic-tool-calls-total",
          "agentic-avg-tokens-per-call-by-process");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldContainExpectedAgenticReportIds() throws IOException {
    // given
    final List<JsonNode> reports = readTemplateReports();

    // when
    final Set<String> reportIds =
        reports.stream().map(report -> report.get("id").asText()).collect(Collectors.toSet());

    // then
    assertThat(reportIds).containsExactlyInAnyOrderElementsOf(EXPECTED_AGENTIC_REPORT_IDS);
  }

  @Test
  void shouldDefineManagementReportsWithBaselineFilters() throws IOException {
    // given
    final List<JsonNode> reports = readTemplateReports();

    // when/then
    assertThat(reports)
        .allSatisfy(
            report -> {
              final JsonNode data = report.get("data");
              assertThat(data.get("managementReport").asBoolean()).isTrue();
              assertThat(data.get("instantPreviewReport").asBoolean()).isFalse();
              assertThat(data.get("definitions")).isEmpty();

              final Set<String> filterTypes =
                  StreamSupport.stream(data.get("filter").spliterator(), false)
                      .map(filter -> filter.get("type").asText())
                      .collect(Collectors.toSet());
              assertThat(filterTypes).contains("completedInstancesOnly", "hasAgentInstances");
            });
  }

  private List<JsonNode> readTemplateReports() throws IOException {
    try (InputStream template =
        InstantPreviewTemplate4Test.class.getResourceAsStream(
            "/instant_preview_dashboards/template4.json")) {
      assertThat(template).isNotNull();
      final JsonNode root = objectMapper.readTree(template);
      assertThat(root.isArray()).isTrue();
      return StreamSupport.stream(root.spliterator(), false).collect(Collectors.toList());
    }
  }
}
