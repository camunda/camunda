/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.entities.dashboard;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardBooleanVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardStringVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import io.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.DashboardIndex;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.exceptions.OptimizeImportFileInvalidException;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.relations.DashboardRelationService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.variable.ProcessVariableService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DashboardImportServiceTest {

  private static final String USER_ID = "testUser";

  private final ProcessVariableService processVariableService = mock(ProcessVariableService.class);
  private DashboardImportService underTest;

  @BeforeEach
  public void setUp() {
    final ConfigurationService configurationService =
        mock(ConfigurationService.class, RETURNS_DEEP_STUBS);
    when(configurationService.getEntityConfiguration().getNameMaxLength()).thenReturn(255);

    // the real DashboardService is used on purpose: the bug lives in its filter validation
    final DashboardService dashboardService =
        new DashboardService(
            mock(DashboardWriter.class),
            mock(DashboardReader.class),
            processVariableService,
            mock(ReportService.class),
            mock(AuthorizedCollectionService.class),
            mock(AbstractIdentityService.class),
            mock(ReportReader.class),
            mock(DashboardRelationService.class),
            configurationService);
    underTest =
        new DashboardImportService(
            mock(DashboardWriter.class), dashboardService, mock(OptimizeIndexNameService.class));
  }

  @Test
  public void shouldNotRequireVariableFilterToExistInReportsWhenValidatingDashboardsForImport() {
    // given
    // the tile still references the report ID of the source environment, which is unknown in the
    // target environment until the reports have been imported
    final DashboardDefinitionExportDto dashboard =
        dashboardWithFilters(
            List.of(
                variableFilter(
                    new DashboardStringVariableFilterDataDto(
                        "myVariable",
                        new DashboardVariableFilterSubDataDto(null, List.of(), true),
                        List.of()))));

    // when / then
    assertThatCode(() -> underTest.validateAllDashboardsOrFail(USER_ID, List.of(dashboard)))
        .doesNotThrowAnyException();
    verify(processVariableService, never())
        .getVariableNamesForAuthorizedReports(anyString(), anyList());
  }

  @Test
  public void shouldNotRequireVariableFilterToExistInReportsWhenValidatingWithoutUser() {
    // given
    final DashboardDefinitionExportDto dashboard =
        dashboardWithFilters(
            List.of(
                variableFilter(
                    new DashboardStringVariableFilterDataDto(
                        "myVariable",
                        new DashboardVariableFilterSubDataDto(null, List.of(), true),
                        List.of()))));

    // when / then
    assertThatCode(() -> underTest.validateAllDashboardsOrFail(List.of(dashboard)))
        .doesNotThrowAnyException();
    verify(processVariableService, never()).getVariableNamesForAuthorizedReports(any(), anyList());
  }

  @Test
  public void shouldStillRejectImportedDashboardWithMalformedVariableFilter() {
    // given
    // boolean variable filters must not carry sub data
    final DashboardDefinitionExportDto dashboard =
        dashboardWithFilters(
            List.of(
                variableFilter(
                    new DashboardBooleanVariableFilterDataDto(
                        "myVariable",
                        new DashboardVariableFilterSubDataDto(null, List.of(), true),
                        null))));

    // when / then
    assertThatThrownBy(() -> underTest.validateAllDashboardsOrFail(USER_ID, List.of(dashboard)))
        .isInstanceOf(OptimizeImportFileInvalidException.class)
        .hasMessageContaining("invalid filters")
        .hasMessageContaining("Filter subdata cannot be supplied");
  }

  @Test
  public void shouldStillRejectImportedDashboardWithVariableFilterWithoutData() {
    // given
    final DashboardDefinitionExportDto dashboard =
        dashboardWithFilters(List.of(new DashboardVariableFilterDto()));

    // when / then
    assertThatThrownBy(() -> underTest.validateAllDashboardsOrFail(USER_ID, List.of(dashboard)))
        .isInstanceOf(OptimizeImportFileInvalidException.class)
        .hasMessageContaining("invalid filters")
        .hasMessageContaining("All filters need to supply Filter data");
  }

  private static DashboardDefinitionExportDto dashboardWithFilters(
      final List<DashboardFilterDto<?>> filters) {
    final DashboardReportTileDto tile = new DashboardReportTileDto();
    tile.setId(IdGenerator.getNextId());
    tile.setType(DashboardTileType.OPTIMIZE_REPORT);

    final DashboardDefinitionExportDto dashboard = new DashboardDefinitionExportDto();
    dashboard.setId(IdGenerator.getNextId());
    dashboard.setName("Imported dashboard");
    dashboard.setSourceIndexVersion(DashboardIndex.VERSION);
    dashboard.setTiles(List.of(tile));
    dashboard.setAvailableFilters(filters);
    return dashboard;
  }

  private static DashboardVariableFilterDto variableFilter(
      final DashboardVariableFilterDataDto data) {
    final DashboardVariableFilterDto filter = new DashboardVariableFilterDto();
    filter.setData(data);
    return filter;
  }
}
