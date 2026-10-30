/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.service.db.report.PlainReportEvaluationHandler;
import io.camunda.optimize.service.report.ReportService;
import java.util.List;
import org.junit.jupiter.api.Test;

class KpiServiceTest {

  private static final String PROCESS_KEY = "process";

  private final ReportService reportService = mock(ReportService.class);
  private final KpiService underTest =
      new KpiService(
          reportService, mock(LocalizationService.class), mock(PlainReportEvaluationHandler.class));

  @Test
  @SuppressWarnings({"deprecation", "removal", "BoxedPrimitiveConstructor"})
  void shouldTreatNonCachedTrueAsKpi() {
    // given
    // a Boolean that is equal to TRUE but is not the Boolean.TRUE instance
    final Boolean nonCachedTrue = new Boolean(true);
    final SingleProcessReportDefinitionRequestDto kpiReport = report(nonCachedTrue);
    final SingleProcessReportDefinitionRequestDto nonKpiReport = report(false);
    final SingleProcessReportDefinitionRequestDto unsetKpiReport = report(null);
    when(reportService.getAllReportsForProcessDefinitionKeyOmitXml(PROCESS_KEY))
        .thenReturn(List.of(kpiReport, nonKpiReport, unsetKpiReport));

    // when
    final var result = underTest.getValidKpiReportsForProcessDefinition(PROCESS_KEY);

    // then
    assertThat(result).containsExactly(kpiReport);
  }

  private static SingleProcessReportDefinitionRequestDto report(final Boolean isKpi) {
    final ProcessReportDataDto data = new ProcessReportDataDto();
    data.setDefinitions(List.of(new ReportDataDefinitionDto(PROCESS_KEY)));
    data.setView(new ProcessViewDto());
    data.setGroupBy(new NoneGroupByDto());
    final SingleReportTargetValueDto targetValue = new SingleReportTargetValueDto();
    targetValue.setIsKpi(isKpi);
    data.getConfiguration().setTargetValue(targetValue);
    final SingleProcessReportDefinitionRequestDto report =
        new SingleProcessReportDefinitionRequestDto();
    report.setData(data);
    return report;
  }
}
