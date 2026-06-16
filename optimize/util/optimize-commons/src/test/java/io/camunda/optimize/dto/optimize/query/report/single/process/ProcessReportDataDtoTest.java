/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessDefinitionKeyGroupByDto;
import org.junit.jupiter.api.Test;

class ProcessReportDataDtoTest {

  @Test
  void shouldSupportGroupByPaginationForAgenticReportGroupedByProcessDefinitionKey() {
    // given
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setAgenticControlReport(true);
    reportData.setGroupBy(new ProcessDefinitionKeyGroupByDto());

    // then
    assertThat(reportData.isGroupByPaginationSupported()).isTrue();
  }

  @Test
  void shouldNotSupportGroupByPaginationForAgenticReportWithOtherGroupBy() {
    // given an agentic report that is not grouped by process definition key (e.g. a number tile)
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setAgenticControlReport(true);
    reportData.setGroupBy(new NoneGroupByDto());

    // then it keeps rejecting pagination like other non-paginatable reports
    assertThat(reportData.isGroupByPaginationSupported()).isFalse();
  }

  @Test
  void shouldNotSupportGroupByPaginationForNonAgenticReportGroupedByProcessDefinitionKey() {
    // given a regular report grouped by process definition key
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setAgenticControlReport(false);
    reportData.setGroupBy(new ProcessDefinitionKeyGroupByDto());

    // then pagination is not enabled
    assertThat(reportData.isGroupByPaginationSupported()).isFalse();
  }
}
