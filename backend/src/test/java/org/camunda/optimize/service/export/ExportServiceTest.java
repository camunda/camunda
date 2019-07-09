/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionRawDataReportResult;
import org.camunda.optimize.service.es.report.result.process.SingleProcessRawDataReportResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ExportServiceTest {

  @Mock
  private AuthorizationCheckReportEvaluationHandler reportService;

  @Mock
  private ConfigurationService configurationService;

  @InjectMocks
  private ExportService exportService;

  @Before
  public void init() {
    when(configurationService.getExportCsvLimit()).thenReturn(100);
    when(configurationService.getExportCsvOffset()).thenReturn(0);
  }

  @Test
  public void rawProcessReportCsvExport() throws IOException {
    // given
    final RawDataProcessReportResultDto rawDataProcessReportResultDto = new RawDataProcessReportResultDto();
    rawDataProcessReportResultDto.setData(RawDataHelper.getRawDataProcessInstanceDtos());
    SingleProcessRawDataReportResult rawDataReportResult =
      new SingleProcessRawDataReportResult(rawDataProcessReportResultDto, new SingleProcessReportDefinitionDto());
    when(reportService.evaluateSavedReport(any(), any(), any())).thenReturn(rawDataReportResult);

    // when
    Optional<byte[]> csvContent = exportService.getCsvBytesForEvaluatedReportResult("", "", Collections.emptySet());

    assertThat(csvContent.isPresent(), is(true));

    byte[] expectedContent = readFileFromClasspath("/csv/process/single/raw_process_data.csv");
    assertThat(csvContent.get(), is(expectedContent));
  }

  @Test
  public void rawDecisionReportCsvExport() throws IOException {
    // given
    final RawDataDecisionReportResultDto rawDataDecisionReportResultDto = new RawDataDecisionReportResultDto();
    rawDataDecisionReportResultDto.setData(RawDataHelper.getRawDataDecisionInstanceDtos());
    SingleDecisionRawDataReportResult rawDataReportResult =
      new SingleDecisionRawDataReportResult(rawDataDecisionReportResultDto, new SingleDecisionReportDefinitionDto());
    when(reportService.evaluateSavedReport(any(), any(), any())).thenReturn(rawDataReportResult);

    // when
    Optional<byte[]> csvContent = exportService.getCsvBytesForEvaluatedReportResult("", "", Collections.emptySet());

    assertThat(csvContent.isPresent(), is(true));

    byte[] expectedContent = readFileFromClasspath("/csv/decision/raw_decision_data.csv");
    assertThat(csvContent.get(), is(expectedContent));
  }

  private byte[] readFileFromClasspath(final String s) throws IOException {
    Path path = Paths.get(this.getClass().getResource(s).getPath());
    return Files.readAllBytes(path);
  }


}