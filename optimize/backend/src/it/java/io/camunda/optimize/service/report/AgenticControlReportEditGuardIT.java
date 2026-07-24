/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.report;

import static io.camunda.optimize.service.dashboard.AgenticControlDashboardService.TOKEN_TREND_REPORT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.service.dashboard.AgenticControlDashboardService;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Agentic control reports are system-generated ({@code data.agenticControlReport = true}) and must
 * not be mutated by users. This verifies the {@link ReportService} guard now recognises them
 * alongside management and instant-preview reports, so a manual delete is rejected rather than
 * removing a report that the dashboard would recreate on the next reconcile. Runs against whichever
 * backend the IT suite is configured with (Elasticsearch or OpenSearch).
 */
class AgenticControlReportEditGuardIT extends AbstractBrokerlessZeebeCCSMIT {

  private ReportService reportService;
  private ReportReader reportReader;

  @BeforeEach
  void setUp() {
    // seeds the agentic control dashboard and its system-generated reports
    embeddedOptimizeExtension.getBean(AgenticControlDashboardService.class).reconcile();
    reportService = embeddedOptimizeExtension.getBean(ReportService.class);
    reportReader = embeddedOptimizeExtension.getBean(ReportReader.class);
  }

  @Test
  void shouldRejectDeletingAgenticControlReport() {
    // given an agentic control report was seeded by the reconcile above
    assertThat(reportReader.getReport(TOKEN_TREND_REPORT_ID)).isPresent();

    // when deleting it manually, then the system-generated guard rejects it
    assertThatThrownBy(() -> reportService.deleteReport(TOKEN_TREND_REPORT_ID))
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("System-generated reports cannot be deleted");

    // and the report is left untouched
    assertThat(reportReader.getReport(TOKEN_TREND_REPORT_ID)).isPresent();
  }
}
