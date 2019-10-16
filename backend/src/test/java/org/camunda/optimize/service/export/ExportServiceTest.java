/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionRawDataReportResult;
import org.camunda.optimize.service.es.report.result.process.SingleProcessRawDataReportResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExportServiceTest {

  @Mock
  private AuthorizationCheckReportEvaluationHandler reportService;

  @Mock
  private ConfigurationService configurationService;

  @InjectMocks
  private ExportService exportService;

  @BeforeEach
  public void init() {
    when(configurationService.getExportCsvLimit()).thenReturn(100);
  }

  @Test
  public void rawProcessReportCsvExport() {
    // given
    final RawDataProcessReportResultDto rawDataProcessReportResultDto = new RawDataProcessReportResultDto();
    rawDataProcessReportResultDto.setData(RawDataHelper.getRawDataProcessInstanceDtos());
    SingleProcessRawDataReportResult rawDataReportResult =
      new SingleProcessRawDataReportResult(rawDataProcessReportResultDto, new SingleProcessReportDefinitionDto());
    when(reportService.evaluateSavedReport(any(), any(), any())).thenReturn(new AuthorizedReportEvaluationResult(
      rawDataReportResult,
      RoleType.VIEWER
    ));

    // when
    Optional<byte[]> csvContent = exportService.getCsvBytesForEvaluatedReportResult("", "", Collections.emptySet());
    assertThat(csvContent.isPresent(), is(true));

    String actualContent = new String(csvContent.get());
    String expectedContent = FileReaderUtil.readFileWithWindowsLineSeparator(
      "/csv/process/single/raw_process_data.csv"
    );

    assertThat(actualContent, is(expectedContent));
  }

  @Test
  public void rawDecisionReportCsvExport() {
    // given
    final RawDataDecisionReportResultDto rawDataDecisionReportResultDto = new RawDataDecisionReportResultDto();
    rawDataDecisionReportResultDto.setData(RawDataHelper.getRawDataDecisionInstanceDtos());
    SingleDecisionRawDataReportResult rawDataReportResult =
      new SingleDecisionRawDataReportResult(rawDataDecisionReportResultDto, new SingleDecisionReportDefinitionDto());
    when(reportService.evaluateSavedReport(any(), any(), any())).thenReturn(new AuthorizedReportEvaluationResult(
      rawDataReportResult,
      RoleType.VIEWER
    ));

    // when
    Optional<byte[]> csvContent = exportService.getCsvBytesForEvaluatedReportResult("", "", Collections.emptySet());

    assertThat(csvContent.isPresent(), is(true));

    String actualContent = new String(csvContent.get());
    String expectedContent = FileReaderUtil.readFileWithWindowsLineSeparator(
      "/csv/decision/raw_decision_data.csv"
    );
    assertThat(actualContent, is(expectedContent));
  }
}