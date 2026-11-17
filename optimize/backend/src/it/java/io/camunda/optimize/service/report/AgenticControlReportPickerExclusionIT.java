/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.report;

import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.DURATION_STABILITY_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.FAILURE_RATE_BY_VERSION_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_AVG_DURATION_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_AVG_TOKENS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_COMPLETED_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_DURATION_P50_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_DURATION_P95_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_INCIDENT_RATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_MEDIAN_TOKENS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.KPI_TOOL_CALLS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.TOKEN_CONSUMERS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.TOKEN_OUTLIER_BANDS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.TOKEN_TREND_REPORT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.dashboard.AgenticControlDashboardService;
import io.camunda.optimize.service.db.reader.ReportReader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the dashboard "Add a Report" picker, which is backed by {@link
 * ReportReader#getAllPrivateReportsOmitXml()} ({@code GET api/report}). Agentic control reports are
 * system-generated implementation details flagged with {@code data.agenticControlReport = true};
 * they must not be selectable when users build their own dashboards. This test runs against
 * whichever backend the IT suite is configured with, covering both Elasticsearch and OpenSearch.
 */
class AgenticControlReportPickerExclusionIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final List<String> AGENTIC_REPORT_IDS =
      List.of(
          KPI_COMPLETED_REPORT_ID,
          KPI_AVG_DURATION_REPORT_ID,
          KPI_INCIDENT_RATE_REPORT_ID,
          KPI_AVG_TOKENS_REPORT_ID,
          KPI_MEDIAN_TOKENS_REPORT_ID,
          DURATION_STABILITY_REPORT_ID,
          TOKEN_TREND_REPORT_ID,
          TOKEN_CONSUMERS_REPORT_ID,
          TOKEN_OUTLIER_BANDS_REPORT_ID,
          KPI_DURATION_P50_REPORT_ID,
          KPI_DURATION_P95_REPORT_ID,
          FAILURE_RATE_BY_VERSION_REPORT_ID,
          KPI_TOOL_CALLS_REPORT_ID);

  private ReportReader reportReader;

  @BeforeEach
  void setUp() {
    // seeds the agentic control dashboard and its system-generated reports
    embeddedOptimizeExtension.getBean(AgenticControlDashboardService.class).reconcile();
    reportReader = embeddedOptimizeExtension.getBean(ReportReader.class);
  }

  @Test
  void shouldNotReturnAgenticControlReportsInPrivateReportPicker() {
    // given the agentic reports were actually persisted by the reconcile above
    assertThat(reportReader.getReport(KPI_COMPLETED_REPORT_ID)).isPresent();

    // when fetching the private reports backing the "Add a Report" picker
    final List<ReportDefinitionDto> reports = reportReader.getAllPrivateReportsOmitXml();

    // then none of the agentic control reports leak into the picker
    assertThat(reports)
        .extracting(ReportDefinitionDto::getId)
        .doesNotContainAnyElementsOf(AGENTIC_REPORT_IDS);
    assertThat(reports)
        .noneMatch(
            report ->
                report.getData() instanceof final ProcessReportDataDto data
                    && data.isAgenticControlReport());
  }
}
