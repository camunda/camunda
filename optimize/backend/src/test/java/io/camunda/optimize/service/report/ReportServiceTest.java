/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.report;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.relations.ReportRelationService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.security.ReportAuthorizationService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

  private static final String BPMN_PROCESS_ID = "invoice-process";
  private static final String OTHER_BPMN_PROCESS_ID = "shipping-process";

  private ReportReader reportReader;
  private ReportWriter reportWriter;
  private ReportService reportService;

  @BeforeEach
  void init() {
    reportReader = mock(ReportReader.class);
    reportWriter = mock(ReportWriter.class);
    reportService =
        new ReportService(
            reportWriter,
            reportReader,
            mock(ReportAuthorizationService.class),
            mock(ReportRelationService.class),
            mock(AuthorizedCollectionService.class),
            mock(AbstractIdentityService.class),
            mock(DefinitionService.class),
            mock(ConfigurationService.class));
  }

  @Test
  void shouldClearXmlForSingleDefinitionReport() {
    // given
    final SingleProcessReportDefinitionRequestDto report =
        singleProcessReport("report-1", BPMN_PROCESS_ID);
    when(reportReader.getAllReportsForProcessDefinitionKeyOmitXml(BPMN_PROCESS_ID))
        .thenReturn(List.of(report));

    // when
    reportService.clearCachedReportXml(BPMN_PROCESS_ID);

    // then
    verify(reportWriter).clearReportDefinitionXmlForReportIds(List.of("report-1"));
  }

  @Test
  void shouldClearXmlForComparisonReportWhereKeyIsFirstListedDefinition() {
    // given -- a comparison report referencing BPMN_PROCESS_ID as its first of two definitions
    final ProcessReportDataDto comparisonData = new ProcessReportDataDto();
    comparisonData.setProcessDefinitionKey(BPMN_PROCESS_ID);
    comparisonData.getDefinitions().add(new ReportDataDefinitionDto(OTHER_BPMN_PROCESS_ID));
    final SingleProcessReportDefinitionRequestDto report =
        new SingleProcessReportDefinitionRequestDto(comparisonData);
    report.setId("report-3");
    when(reportReader.getAllReportsForProcessDefinitionKeyOmitXml(BPMN_PROCESS_ID))
        .thenReturn(List.of(report));

    // when
    reportService.clearCachedReportXml(BPMN_PROCESS_ID);

    // then
    verify(reportWriter).clearReportDefinitionXmlForReportIds(List.of("report-3"));
  }

  @Test
  void shouldNotClearXmlForReportWhereKeyIsNotFirstListedDefinition() {
    // given -- a comparison report referencing BPMN_PROCESS_ID only as its second definition
    final ProcessReportDataDto comparisonData = new ProcessReportDataDto();
    comparisonData.setProcessDefinitionKey(OTHER_BPMN_PROCESS_ID);
    comparisonData.getDefinitions().add(new ReportDataDefinitionDto(BPMN_PROCESS_ID));
    final SingleProcessReportDefinitionRequestDto report =
        new SingleProcessReportDefinitionRequestDto(comparisonData);
    report.setId("report-2");
    when(reportReader.getAllReportsForProcessDefinitionKeyOmitXml(BPMN_PROCESS_ID))
        .thenReturn(List.of(report));

    // when
    reportService.clearCachedReportXml(BPMN_PROCESS_ID);

    // then
    verify(reportWriter, never()).clearReportDefinitionXmlForReportIds(anyList());
  }

  @Test
  void shouldIgnoreCombinedReportsAndNotClearXml() {
    // given
    final CombinedReportDefinitionRequestDto combinedReport =
        new CombinedReportDefinitionRequestDto();
    combinedReport.setId("combined-1");
    when(reportReader.getAllReportsForProcessDefinitionKeyOmitXml(BPMN_PROCESS_ID))
        .thenReturn(List.of(combinedReport));

    // when
    reportService.clearCachedReportXml(BPMN_PROCESS_ID);

    // then
    verify(reportWriter, never()).clearReportDefinitionXmlForReportIds(anyList());
  }

  @Test
  void shouldNotCallWriterWhenNoCandidatesFound() {
    // given
    when(reportReader.getAllReportsForProcessDefinitionKeyOmitXml(BPMN_PROCESS_ID))
        .thenReturn(List.of());

    // when
    reportService.clearCachedReportXml(BPMN_PROCESS_ID);

    // then
    verify(reportWriter, never()).clearReportDefinitionXmlForReportIds(anyList());
  }

  private SingleProcessReportDefinitionRequestDto singleProcessReport(
      final String reportId, final String definitionKey) {
    final ProcessReportDataDto data = new ProcessReportDataDto();
    data.setProcessDefinitionKey(definitionKey);
    final SingleProcessReportDefinitionRequestDto report =
        new SingleProcessReportDefinitionRequestDto(data);
    report.setId(reportId);
    return report;
  }
}
