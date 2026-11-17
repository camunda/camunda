/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardStringVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.collection.CollectionService;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.entities.dashboard.DashboardImportService;
import io.camunda.optimize.service.entities.report.ReportImportService;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.relations.DashboardRelationService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.variable.ProcessVariableService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class EntityImportDashboardFlowTest {

  private static final String DEFINITION_KEY = "invoice";
  private static final String VARIABLE_NAME = "myVariable";
  private static final String NEW_REPORT_ID = "new-report-id";
  private static final String NEW_DASHBOARD_ID = "new-dashboard-id";

  private final ReportWriter reportWriter = mock(ReportWriter.class);
  private final DashboardWriter dashboardWriter = mock(DashboardWriter.class);
  private final ProcessVariableService processVariableService = mock(ProcessVariableService.class);
  private EntityImportService underTest;

  @BeforeEach
  public void setUp() {
    final ConfigurationService configurationService =
        mock(ConfigurationService.class, RETURNS_DEEP_STUBS);
    when(configurationService.getEntityConfiguration().getNameMaxLength()).thenReturn(255);

    final DefinitionService definitionService = mock(DefinitionService.class);
    when(definitionService.getDefinitionVersions(
            eq(DefinitionType.PROCESS), eq(DEFINITION_KEY), anyList()))
        .thenReturn(List.of(new DefinitionVersionResponseDto("1", null)));

    when(reportWriter.createNewSingleProcessReport(
            anyString(), any(ProcessReportDataDto.class), anyString(), any(), any()))
        .thenReturn(new IdResponseDto(NEW_REPORT_ID));
    when(dashboardWriter.createNewDashboard(anyString(), any(DashboardDefinitionRestDto.class)))
        .thenReturn(new IdResponseDto(NEW_DASHBOARD_ID));

    final OptimizeIndexNameService indexNameService = mock(OptimizeIndexNameService.class);
    when(indexNameService.getIndexPrefix()).thenReturn("");

    final DataSourceDefinitionAuthorizationService definitionAuthorizationService =
        mock(DataSourceDefinitionAuthorizationService.class);
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            anyString(), any(DefinitionType.class), anyString(), anyList()))
        .thenReturn(true);

    // real services throughout the import flow, only the persistence and lookups are mocked
    final ReportImportService reportImportService =
        new ReportImportService(
            mock(ReportService.class),
            reportWriter,
            definitionService,
            definitionAuthorizationService,
            indexNameService);
    final DashboardService dashboardService =
        new DashboardService(
            dashboardWriter,
            mock(DashboardReader.class),
            processVariableService,
            mock(ReportService.class),
            mock(AuthorizedCollectionService.class),
            mock(AbstractIdentityService.class),
            mock(ReportReader.class),
            mock(DashboardRelationService.class),
            configurationService);
    final DashboardImportService dashboardImportService =
        new DashboardImportService(
            dashboardWriter, dashboardService, mock(OptimizeIndexNameService.class));
    underTest =
        new EntityImportService(
            reportImportService,
            dashboardImportService,
            mock(AuthorizedCollectionService.class),
            mock(CollectionService.class));
  }

  @Test
  public void shouldImportDashboardWithVariableFilterAndPointItsTileToTheImportedReport() {
    // given
    final String originalReportId = IdGenerator.getNextId();
    final String originalDashboardId = IdGenerator.getNextId();
    final Set<OptimizeEntityExportDto> exportedEntities =
        Set.of(
            exportedReport(originalReportId),
            exportedDashboardWithVariableFilter(originalDashboardId, originalReportId));

    // when
    final List<EntityIdResponseDto> importedEntities =
        underTest.importEntities(null, exportedEntities);

    // then
    assertDashboardImportedWithRemappedTileAndPreservedFilter(importedEntities);
  }

  @Test
  public void shouldImportDashboardWithVariableFilterAsUser() {
    // given
    final String originalReportId = IdGenerator.getNextId();
    final String originalDashboardId = IdGenerator.getNextId();
    final Set<OptimizeEntityExportDto> exportedEntities =
        Set.of(
            exportedReport(originalReportId),
            exportedDashboardWithVariableFilter(originalDashboardId, originalReportId));

    // when
    final List<EntityIdResponseDto> importedEntities =
        underTest.importEntitiesAsUser("testUser", null, exportedEntities);

    // then
    assertDashboardImportedWithRemappedTileAndPreservedFilter(importedEntities);
  }

  private void assertDashboardImportedWithRemappedTileAndPreservedFilter(
      final List<EntityIdResponseDto> importedEntities) {
    assertThat(importedEntities)
        .containsExactlyInAnyOrder(
            new EntityIdResponseDto(NEW_REPORT_ID, EntityType.REPORT),
            new EntityIdResponseDto(NEW_DASHBOARD_ID, EntityType.DASHBOARD));

    final ArgumentCaptor<DashboardDefinitionRestDto> writtenDashboard =
        ArgumentCaptor.forClass(DashboardDefinitionRestDto.class);
    verify(dashboardWriter).createNewDashboard(anyString(), writtenDashboard.capture());
    assertThat(writtenDashboard.getValue().getTiles())
        .extracting(DashboardReportTileDto::getId)
        .containsExactly(NEW_REPORT_ID);
    assertThat(writtenDashboard.getValue().getAvailableFilters())
        .singleElement()
        .isInstanceOfSatisfying(
            DashboardVariableFilterDto.class,
            filter -> assertThat(filter.getData().getName()).isEqualTo(VARIABLE_NAME));
  }

  private static SingleProcessReportDefinitionExportDto exportedReport(final String reportId) {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey(DEFINITION_KEY);
    reportData.setProcessDefinitionVersions(new ArrayList<>(List.of("1")));

    final SingleProcessReportDefinitionExportDto report =
        new SingleProcessReportDefinitionExportDto(reportData);
    report.setId(reportId);
    report.setName("Imported report");
    report.setSourceIndexVersion(SingleProcessReportIndex.VERSION);
    return report;
  }

  private static DashboardDefinitionExportDto exportedDashboardWithVariableFilter(
      final String dashboardId, final String reportId) {
    final DashboardReportTileDto tile = new DashboardReportTileDto();
    tile.setId(reportId);
    tile.setType(DashboardTileType.OPTIMIZE_REPORT);

    final DashboardVariableFilterDto variableFilter = new DashboardVariableFilterDto();
    variableFilter.setData(
        new DashboardStringVariableFilterDataDto(
            VARIABLE_NAME,
            new DashboardVariableFilterSubDataDto(null, List.of(), true),
            List.of()));

    final DashboardDefinitionRestDto dashboardDefinition = new DashboardDefinitionRestDto();
    dashboardDefinition.setId(dashboardId);
    dashboardDefinition.setName("Imported dashboard");
    dashboardDefinition.setTiles(List.of(tile));
    dashboardDefinition.setAvailableFilters(List.of(variableFilter));

    final DashboardDefinitionExportDto dashboard =
        new DashboardDefinitionExportDto(dashboardDefinition);
    return dashboard;
  }
}
