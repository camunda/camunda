/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.report;

import static io.camunda.optimize.service.dashboard.BusinessValueDashboardService.WORK_HANDLED_TOTAL_REPORT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.service.dashboard.BusinessValueDashboardService;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Business Value Dashboard reports are system-generated ({@code data.businessValueReport = true})
 * and must not be mutated by users — otherwise the next reconcile would recreate the report they
 * just deleted, or overwrite an edit that was never persisted. This verifies the {@link
 * ReportService} guard recognises the {@code businessValueReport} flag alongside {@code
 * agenticControlReport}, mirroring {@code AgenticControlReportEditGuardIT}.
 */
class BusinessValueReportEditGuardIT extends AbstractBrokerlessZeebeCCSMIT {

  private ReportService reportService;
  private ReportReader reportReader;

  @BeforeEach
  void setUp() {
    // seeds the business value dashboard and its system-generated reports
    embeddedOptimizeExtension.getBean(BusinessValueDashboardService.class).reconcile();
    reportService = embeddedOptimizeExtension.getBean(ReportService.class);
    reportReader = embeddedOptimizeExtension.getBean(ReportReader.class);
  }

  @Test
  void shouldRejectDeletingBusinessValueReport() {
    // given a business value report was seeded by the reconcile above
    assertThat(reportReader.getReport(WORK_HANDLED_TOTAL_REPORT_ID)).isPresent();

    // when deleting it manually, then the system-generated guard rejects it
    assertThatThrownBy(() -> reportService.deleteReport(WORK_HANDLED_TOTAL_REPORT_ID))
        .isInstanceOf(OptimizeValidationException.class)
        .hasMessageContaining("System-generated reports cannot be deleted");

    // and the report is left untouched
    assertThat(reportReader.getReport(WORK_HANDLED_TOTAL_REPORT_ID)).isPresent();
  }
}
