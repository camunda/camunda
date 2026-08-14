/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.AGENT_PRESENCE_BY_PROCESS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.AUTOMATION_RATE_AGGREGATE_L1_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.AUTOMATION_RATE_AGGREGATE_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.AUTOMATION_RATE_BY_PROCESS_REPORT_ID;
import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.BUSINESS_VALUE_DASHBOARD_ID;
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
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.reader.ReportReader;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link BusinessValueDashboardService#reconcile()} seeds the dashboard and all
 * thirteen backing reports, and that running it a second time does not duplicate reports, mutate
 * the deterministic ids, or drop the {@code businessValueReport = true} marker that the guard,
 * picker, and evaluation pipelines rely on. Runs against whichever backend the IT suite is
 * configured with (Elasticsearch or OpenSearch).
 *
 * <p>The {@code @BeforeEach} reconcile is required because {@link
 * AbstractBrokerlessZeebeCCSMIT#cleanupOptimizeData()} wipes all Optimize data after every test —
 * so the {@link org.springframework.boot.context.event.ApplicationReadyEvent}-driven seed from
 * container startup is only visible to the first test in the class, and relying on that would make
 * the outcome depend on JUnit's test ordering.
 */
class BusinessValueDashboardReconcileIT extends AbstractBrokerlessZeebeCCSMIT {

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

  private BusinessValueDashboardService service;
  private ReportReader reportReader;
  private DashboardReader dashboardReader;

  @BeforeEach
  void setUp() {
    service = embeddedOptimizeExtension.getBean(BusinessValueDashboardService.class);
    reportReader = embeddedOptimizeExtension.getBean(ReportReader.class);
    dashboardReader = embeddedOptimizeExtension.getBean(DashboardReader.class);
    // reseed after the previous test's cleanup wiped the dashboard + reports
    service.reconcile();
  }

  @Test
  void shouldSeedAllTilesAndDashboardOnReconcile() {
    // given reconcile() has just run in setUp

    // then the dashboard exists with the deterministic id, is flagged as system-generated, and
    // carries tiles pointing at all 10 canonical BVD report ids
    final DashboardDefinitionRestDto dashboard =
        dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID).orElseThrow();
    assertThat(dashboard.isBusinessValueDashboard()).isTrue();
    assertThat(dashboard.getTiles())
        .extracting(tile -> tile.getId())
        .containsExactlyInAnyOrderElementsOf(BVD_REPORT_IDS);

    // and every seeded report is present and flagged as system-generated
    for (final String reportId : BVD_REPORT_IDS) {
      final ReportDefinitionDto report = reportReader.getReport(reportId).orElseThrow();
      assertThat(report.getData()).isInstanceOf(ProcessReportDataDto.class);
      assertThat(((ProcessReportDataDto) report.getData()).isBusinessValueReport()).isTrue();
    }
  }

  @Test
  void shouldBeIdempotentAcrossRestarts() {
    // given the first reconcile already ran in setUp (simulating one container startup)

    // when reconcile runs a second time (simulating a restart)
    service.reconcile();

    // then exactly the 10 canonical BVD reports still resolve — deterministic UUID upsert
    // rules out same-id duplicates by construction, so the fanout query returns at most 10
    // matches, and asserting the returned set equals BVD_REPORT_IDS proves none were dropped.
    final List<ReportDefinitionDto> reports =
        reportReader.getAllReportsForIdsOmitXml(BVD_REPORT_IDS);
    final Set<String> reportIds =
        reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toSet());
    assertThat(reportIds).containsExactlyInAnyOrderElementsOf(BVD_REPORT_IDS);

    // and every one of them still carries businessValueReport = true after the second reconcile —
    // guards against a regression that upserts the report with the flag cleared, which the guard
    // and picker pipelines would then silently stop protecting.
    assertThat(reports)
        .allSatisfy(
            report ->
                assertThat(report.getData())
                    .asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.type(
                            ProcessReportDataDto.class))
                    .extracting(ProcessReportDataDto::isBusinessValueReport)
                    .isEqualTo(true));

    // and the dashboard still resolves to the deterministic id and its tiles still reference the
    // same 10 canonical BVD report ids — asserting on the tile ids (not just the count) is what
    // guards the deterministic-UUID contract: if reconcile ever regenerated ids and rewired the
    // dashboard, a count-only assertion would still pass while the guarantee was broken.
    final DashboardDefinitionRestDto dashboard =
        dashboardReader.getDashboard(BUSINESS_VALUE_DASHBOARD_ID).orElseThrow();
    assertThat(dashboard.isBusinessValueDashboard()).isTrue();
    assertThat(dashboard.getTiles())
        .extracting(tile -> tile.getId())
        .containsExactlyInAnyOrderElementsOf(BVD_REPORT_IDS);
  }
}
