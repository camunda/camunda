/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.report.AuthorizationCheckReportEvaluationHandler;
import io.camunda.optimize.service.db.report.result.RawDataCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.util.FileReaderUtil;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CsvExportServiceTest {

  @Mock private AuthorizationCheckReportEvaluationHandler reportService;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ConfigurationService configurationService;

  @SuppressWarnings("checkstyle:MemberName")
  @InjectMocks
  private CsvExportService CSVExportService;

  @BeforeEach
  public void init() {
    when(configurationService.getCsvConfiguration().getExportCsvLimit()).thenReturn(100);
    when(configurationService.getCsvConfiguration().getExportCsvDelimiter()).thenReturn(',');
  }

  @Test
  public void rawProcessReportCsvExport() {
    // given
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(
            RawDataHelper.getRawDataProcessInstanceDtos(), new ProcessReportDataDto());
    when(reportService.evaluateReport(any()))
        .thenReturn(
            new AuthorizedReportEvaluationResult(
                new SingleReportEvaluationResult(
                    new SingleProcessReportDefinitionRequestDto(), rawDataReportResult),
                RoleType.VIEWER));

    // when
    final byte[] csvContent =
        CSVExportService.getCsvBytesForEvaluatedReportResult("", "", ZoneId.of("+1"))
            .orElseThrow(() -> new OptimizeIntegrationTestException("Got no csv response"));
    final String actualContent = new String(csvContent);
    final String expectedContent =
        FileReaderUtil.readFileWithWindowsLineSeparator("/csv/process/single/raw_process_data.csv");

    // Added "\\s+" for fix failing on windows laptops
    assertThat(actualContent.replaceAll("\\s+", ""))
        .isEqualTo(expectedContent.replaceAll("\\s+", ""));
  }

  @Test
  public void rawDecisionReportCsvExport() {
    // given
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(
            RawDataHelper.getRawDataDecisionInstanceDtos(), new ProcessReportDataDto());
    when(reportService.evaluateReport(any()))
        .thenReturn(
            new AuthorizedReportEvaluationResult(
                new SingleReportEvaluationResult(
                    new SingleProcessReportDefinitionRequestDto(), rawDataReportResult),
                RoleType.VIEWER));

    // when
    final byte[] csvContent =
        CSVExportService.getCsvBytesForEvaluatedReportResult("", "", ZoneId.of("+1"))
            .orElseThrow(() -> new OptimizeIntegrationTestException("Got no csv response"));
    final String actualContent = new String(csvContent);
    final String expectedContent =
        FileReaderUtil.readFileWithWindowsLineSeparator("/csv/decision/raw_decision_data.csv");
    // Added "\\s+" for fix failing on windows laptops
    assertThat(actualContent.replaceAll("\\s+", ""))
        .isEqualTo(expectedContent.replaceAll("\\s+", ""));
  }
}
