/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.apache.http.HttpStatus;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@AllArgsConstructor
public class DashboardClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public DashboardDefinitionDto getDashboard(final String dashboardId) {
    return getRequestExecutor()
      .buildGetDashboardRequest(dashboardId)
      .execute(DashboardDefinitionDto.class, HttpStatus.SC_OK);
  }

  public String createEmptyDashboard(final String collectionId) {
    return createDashboard(collectionId, Collections.emptyList());
  }

  public String createDashboard(final String collectionId, final List<String> reportIds) {
    return createDashboard(createSimpleDashboardDefinition(collectionId, reportIds));
  }

  public String createDashboard(final DashboardDefinitionDto dashboardDefinitionDto) {
    return getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  public void updateDashboardWithReports(final String dashboardId,
                                         final List<String> reportIds) {
    final List<ReportLocationDto> reports = reportIds.stream()
      .map(reportId -> {
        ReportLocationDto reportLocationDto = new ReportLocationDto();
        reportLocationDto.setId(reportId);
        return reportLocationDto;
      })
      .collect(Collectors.toList());
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setReports(reports);
    updateDashboard(dashboardId, dashboard);
  }

  public Response updateDashboard(String id, DashboardDefinitionDto updatedDashboard) {
    return getRequestExecutor()
      .buildUpdateDashboardRequest(id, updatedDashboard)
      .execute(HttpStatus.SC_NO_CONTENT);
  }

  public IdDto copyDashboardToCollection(final String dashboardId, final String collectionId) {
    return getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId, collectionId)
      .execute(IdDto.class, 200);
  }

  public void deleteDashboard(final String dashboardId, final boolean force) {
    getRequestExecutor()
      .buildDeleteDashboardRequest(dashboardId, force)
      .execute(204);
  }

  private DashboardDefinitionDto createSimpleDashboardDefinition(String collectionId, List<String> reportIds) {
    DashboardDefinitionDto definitionDto = new DashboardDefinitionDto();
    definitionDto.setName("MyAwesomeDashboard");
    definitionDto.setCollectionId(collectionId);
    definitionDto.setReports(
      reportIds.stream()
        .map(reportId -> ReportLocationDto.builder().id(reportId).build())
        .collect(Collectors.toList())
    );
    return definitionDto;
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
