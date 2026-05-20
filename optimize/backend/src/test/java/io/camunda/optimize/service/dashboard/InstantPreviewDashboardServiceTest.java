/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.dto.optimize.ReportConstants.API_IMPORT_OWNER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.collection.CollectionService;
import io.camunda.optimize.service.db.reader.InstantDashboardMetadataReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.InstantDashboardMetadataWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.entities.EntityImportService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstantPreviewDashboardServiceTest {

  private static final String AGENTIC_COLLECTION_ID = "agentic-control-plane";
  private static final String AGENTIC_DASHBOARD_ID = "agentic-control-plane-dashboard";
  private static final List<String> AGENTIC_REPORT_IDS =
      List.of(
          "agentic-total-runs",
          "agentic-duration-summary",
          "agentic-duration-trend",
          "agentic-incident-count",
          "agentic-incident-rate",
          "agentic-incident-rate-by-version",
          "agentic-tokens-summary",
          "agentic-tokens-trend",
          "agentic-tokens-input-trend",
          "agentic-tokens-output-trend",
          "agentic-tokens-by-process",
          "agentic-process-instance-count-by-process",
          "agentic-tool-calls-total",
          "agentic-avg-tokens-per-call-by-process");

  @Mock private DashboardService dashboardService;
  @Mock private DashboardWriter dashboardWriter;
  @Mock private ReportService reportService;
  @Mock private ReportWriter reportWriter;
  @Mock private InstantDashboardMetadataReader instantDashboardMetadataReader;
  @Mock private InstantDashboardMetadataWriter instantDashboardMetadataWriter;
  @Mock private EntityImportService entityImportService;
  @Mock private DefinitionService definitionService;
  @Mock private CollectionService collectionService;
  @Mock private ConfigurationService configurationService;

  private InstantPreviewDashboardService underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new InstantPreviewDashboardService(
            dashboardService,
            dashboardWriter,
            reportService,
            reportWriter,
            instantDashboardMetadataReader,
            instantDashboardMetadataWriter,
            entityImportService,
            definitionService,
            collectionService,
            configurationService,
            new HashMap<>());
  }

  @Test
  void shouldPublishAgenticReportsAndDashboardToSystemCollectionOnCreate() {
    // given
    when(entityImportService.readExportDtoOrFailIfInvalid(anyString()))
        .thenReturn(buildAgenticTemplateReports());
    when(collectionService.createNewCollectionWithPresetId(
            eq(API_IMPORT_OWNER_NAME), any(), eq(AGENTIC_COLLECTION_ID), eq(true)))
        .thenReturn(Optional.of(new IdResponseDto(AGENTIC_COLLECTION_ID)));
    when(reportService.getReportDefinition(anyString()))
        .thenThrow(new NotFoundException("missing"));
    when(dashboardService.getDashboardDefinitionAsService(AGENTIC_DASHBOARD_ID))
        .thenThrow(new NotFoundException("missing"));

    // when
    underTest.reconcileAgenticControlPlaneReportsFromTemplate();

    // then
    final ArgumentCaptor<PartialCollectionDefinitionRequestDto> collectionCaptor =
        ArgumentCaptor.forClass(PartialCollectionDefinitionRequestDto.class);
    verify(collectionService)
        .createNewCollectionWithPresetId(
            eq(API_IMPORT_OWNER_NAME),
            collectionCaptor.capture(),
            eq(AGENTIC_COLLECTION_ID),
            eq(true));
    assertThat(collectionCaptor.getValue().getName()).isEqualTo("Agentic Control Plane");

    verify(reportWriter, times(AGENTIC_REPORT_IDS.size()))
        .createNewSingleProcessReport(
            eq(API_IMPORT_OWNER_NAME),
            any(ProcessReportDataDto.class),
            anyString(),
            anyString(),
            eq(AGENTIC_COLLECTION_ID),
            anyString());

    final ArgumentCaptor<DashboardDefinitionRestDto> dashboardCaptor =
        ArgumentCaptor.forClass(DashboardDefinitionRestDto.class);
    verify(dashboardWriter).saveDashboard(dashboardCaptor.capture());
    assertThat(dashboardCaptor.getValue().getCollectionId()).isEqualTo(AGENTIC_COLLECTION_ID);
    assertThat(dashboardCaptor.getValue().isManagementDashboard()).isTrue();
  }

  @Test
  void shouldKeepPublishedEntitiesInSystemCollectionOnUpdate() {
    // given
    when(entityImportService.readExportDtoOrFailIfInvalid(anyString()))
        .thenReturn(buildAgenticTemplateReports());
    when(collectionService.createNewCollectionWithPresetId(
            eq(API_IMPORT_OWNER_NAME), any(), eq(AGENTIC_COLLECTION_ID), eq(true)))
        .thenReturn(Optional.empty());

    final SingleProcessReportDefinitionRequestDto existingReport =
        new SingleProcessReportDefinitionRequestDto();
    when(reportService.getReportDefinition(anyString()))
        .thenReturn((ReportDefinitionDto) existingReport);

    final DashboardDefinitionRestDto existingDashboard = new DashboardDefinitionRestDto();
    existingDashboard.setManagementDashboard(true);
    existingDashboard.setCollectionId(AGENTIC_COLLECTION_ID);
    when(dashboardService.getDashboardDefinitionAsService(AGENTIC_DASHBOARD_ID))
        .thenReturn(existingDashboard);

    // when
    underTest.reconcileAgenticControlPlaneReportsFromTemplate();

    // then
    final ArgumentCaptor<SingleProcessReportDefinitionUpdateDto> reportUpdateCaptor =
        ArgumentCaptor.forClass(SingleProcessReportDefinitionUpdateDto.class);
    verify(reportWriter, times(AGENTIC_REPORT_IDS.size()))
        .updateSingleProcessReport(reportUpdateCaptor.capture());
    assertThat(reportUpdateCaptor.getAllValues())
        .extracting(SingleProcessReportDefinitionUpdateDto::getId)
        .containsExactlyInAnyOrderElementsOf(AGENTIC_REPORT_IDS);
    assertThat(reportUpdateCaptor.getAllValues())
        .allSatisfy(
            update -> assertThat(update.getCollectionId()).isEqualTo(AGENTIC_COLLECTION_ID));

    final ArgumentCaptor<DashboardDefinitionUpdateDto> dashboardUpdateCaptor =
        ArgumentCaptor.forClass(DashboardDefinitionUpdateDto.class);
    verify(dashboardWriter)
        .updateDashboard(dashboardUpdateCaptor.capture(), eq(AGENTIC_DASHBOARD_ID));
    verify(dashboardWriter, never()).saveDashboard(any(DashboardDefinitionRestDto.class));
    verify(dashboardWriter, never()).deleteDashboard(anyString());
    verify(reportWriter, never())
        .createNewSingleProcessReport(
            anyString(),
            any(ProcessReportDataDto.class),
            anyString(),
            anyString(),
            anyString(),
            anyString());
    verify(reportWriter, never()).deleteSingleReport(anyString());
  }

  private Set<OptimizeEntityExportDto> buildAgenticTemplateReports() {
    return AGENTIC_REPORT_IDS.stream()
        .map(
            reportId -> {
              final ProcessReportDataDto reportData = new ProcessReportDataDto();
              reportData.setDefinitions(List.of());
              final SingleProcessReportDefinitionExportDto report =
                  new SingleProcessReportDefinitionExportDto(reportData);
              report.setId(reportId);
              report.setName(reportId);
              report.setDescription(reportId);
              return (OptimizeEntityExportDto) report;
            })
        .collect(Collectors.toSet());
  }
}
