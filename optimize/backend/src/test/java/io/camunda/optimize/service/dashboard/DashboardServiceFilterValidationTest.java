/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardStringVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.reader.DashboardReader;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.writer.DashboardWriter;
import io.camunda.optimize.service.exceptions.InvalidDashboardVariableFilterException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class DashboardServiceFilterValidationTest {

  private static final String USER_ID = "testUser";
  private static final String VARIABLE_NAME = "myVariable";

  private final ProcessVariableService processVariableService = mock(ProcessVariableService.class);
  private DashboardService underTest;

  @BeforeEach
  public void setUp() {
    underTest =
        new DashboardService(
            mock(DashboardWriter.class),
            mock(DashboardReader.class),
            processVariableService,
            mock(ReportService.class),
            mock(AuthorizedCollectionService.class),
            mock(AbstractIdentityService.class),
            mock(ReportReader.class),
            mock(DashboardRelationService.class),
            mock(ConfigurationService.class));
  }

  @Test
  public void shouldRejectVariableFilterThatDoesNotExistInDashboardReports() {
    // given
    when(processVariableService.getVariableNamesForAuthorizedReports(anyString(), anyList()))
        .thenReturn(List.of());

    // when / then
    assertThatThrownBy(
            () -> underTest.validateDashboardFilters(USER_ID, variableFilters(), tiles()))
        .isInstanceOf(InvalidDashboardVariableFilterException.class)
        .hasMessageContaining("do not exist in any report in dashboard");
  }

  @Test
  public void shouldAcceptVariableFilterThatExistsInDashboardReports() {
    // given
    when(processVariableService.getVariableNamesForAuthorizedReports(anyString(), anyList()))
        .thenReturn(
            List.of(new ProcessVariableNameResponseDto(VARIABLE_NAME, VariableType.STRING, null)));

    // when / then
    assertThatCode(() -> underTest.validateDashboardFilters(USER_ID, variableFilters(), tiles()))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  public void shouldRejectVariableFilterWithoutName(final String variableName) {
    // given
    final DashboardVariableFilterDto filter = new DashboardVariableFilterDto();
    filter.setData(
        new DashboardStringVariableFilterDataDto(
            variableName, new DashboardVariableFilterSubDataDto(null, List.of(), true), List.of()));

    // when / then
    assertThatThrownBy(() -> underTest.validateDashboardFilters(USER_ID, List.of(filter), tiles()))
        .isInstanceOf(InvalidDashboardVariableFilterException.class);
  }

  private static List<DashboardFilterDto<?>> variableFilters() {
    final DashboardVariableFilterDto filter = new DashboardVariableFilterDto();
    filter.setData(
        new DashboardStringVariableFilterDataDto(
            VARIABLE_NAME,
            new DashboardVariableFilterSubDataDto(null, List.of(), true),
            List.of()));
    return List.of(filter);
  }

  private static List<DashboardReportTileDto> tiles() {
    final DashboardReportTileDto tile = new DashboardReportTileDto();
    tile.setId(IdGenerator.getNextId());
    tile.setType(DashboardTileType.OPTIMIZE_REPORT);
    return List.of(tile);
  }
}
