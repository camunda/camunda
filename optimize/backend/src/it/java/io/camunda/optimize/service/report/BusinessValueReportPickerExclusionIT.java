/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.report;

import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.AGENT_PRESENCE_BY_PROCESS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.AUTOMATION_RATE_AGGREGATE_L1_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.AUTOMATION_RATE_AGGREGATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.AUTOMATION_RATE_BY_PROCESS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.COUNT_BY_DATE_L1_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.COUNT_BY_DATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.COUNT_BY_PROCESS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.DURATION_AVG_TOTAL_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.DURATION_BY_DATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.DURATION_BY_PROCESS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.DURATION_PERCENTILES_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.VOLUME_TOTAL_L1_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.WORK_HANDLED_TOTAL_REPORT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.dashboard.BusinessValueDashboardService;
import io.camunda.optimize.service.db.reader.ReportReader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the dashboard "Add a Report" picker, which is backed by {@link
 * ReportReader#getAllPrivateReportsOmitXml()}. Business Value Dashboard reports are
 * system-generated implementation details flagged with {@code data.businessValueReport = true};
 * they must not be selectable when users build their own dashboards. Mirrors {@code
 * AgenticControlReportPickerExclusionIT} for the BVD flag.
 */
class BusinessValueReportPickerExclusionIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final List<String> BVD_REPORT_IDS =
      List.of(
          WORK_HANDLED_TOTAL_REPORT_ID,
          VOLUME_TOTAL_L1_REPORT_ID,
          DURATION_AVG_TOTAL_REPORT_ID,
          COUNT_BY_PROCESS_REPORT_ID,
          COUNT_BY_DATE_REPORT_ID,
          COUNT_BY_DATE_L1_REPORT_ID,
          DURATION_BY_PROCESS_REPORT_ID,
          DURATION_PERCENTILES_REPORT_ID,
          DURATION_BY_DATE_REPORT_ID,
          AGENT_PRESENCE_BY_PROCESS_REPORT_ID,
          AUTOMATION_RATE_AGGREGATE_REPORT_ID,
          AUTOMATION_RATE_AGGREGATE_L1_REPORT_ID,
          AUTOMATION_RATE_BY_PROCESS_REPORT_ID);

  private ReportReader reportReader;

  @BeforeEach
  void setUp() {
    // seeds the business value dashboard and its system-generated reports
    embeddedOptimizeExtension.getBean(BusinessValueDashboardService.class).reconcile();
    reportReader = embeddedOptimizeExtension.getBean(ReportReader.class);
  }

  @Test
  void shouldNotReturnBusinessValueReportsInPrivateReportPicker() {
    // given the BVD reports were actually persisted by the reconcile above
    assertThat(reportReader.getReport(WORK_HANDLED_TOTAL_REPORT_ID)).isPresent();

    // when fetching the private reports backing the "Add a Report" picker
    final List<ReportDefinitionDto> reports = reportReader.getAllPrivateReportsOmitXml();

    // then none of the BVD reports leak into the picker, and no report data still carries the
    // businessValueReport marker — protects against a future picker path that forgets to filter
    assertThat(reports)
        .extracting(ReportDefinitionDto::getId)
        .doesNotContainAnyElementsOf(BVD_REPORT_IDS);
    assertThat(reports)
        .noneMatch(
            report ->
                report.getData() instanceof final ProcessReportDataDto data
                    && data.isBusinessValueReport());
  }
}
