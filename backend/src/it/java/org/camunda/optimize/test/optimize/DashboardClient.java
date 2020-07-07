/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionDto;

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
      .execute(DashboardDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public AuthorizedDashboardDefinitionDto getDashboardAsUser(final String dashboardId, String username,
                                                             String password) {
    return getRequestExecutor()
      .buildGetDashboardRequest(dashboardId)
      .withUserAuthentication(username, password)
      .execute(AuthorizedDashboardDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public String createEmptyDashboard(final String collectionId) {
    return createDashboard(collectionId, Collections.emptyList());
  }

  public String createDashboard(final String collectionId, final List<String> reportIds) {
    return createDashboard(createSimpleDashboardDefinition(collectionId, reportIds));
  }

  public Response createDashboardAsUserGetRawResponse(final String collectionId, final List<String> reportIds,
                                                      String username, String password) {
    return getRequestExecutor()
      .buildCreateDashboardRequest(createSimpleDashboardDefinition(collectionId, reportIds))
      .withUserAuthentication(username, password)
      .execute();
  }

  public String createDashboardAsUser(final DashboardDefinitionDto dashboardDefinitionDto, String username,
                                      String password) {
    return getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .withUserAuthentication(username, password)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public String createDashboard(final DashboardDefinitionDto dashboardDefinitionDto) {
    return getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public Response updateDashboardWithReports(final String dashboardId,
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
    return updateDashboard(dashboardId, dashboard);
  }

  public Response updateDashboard(String id, DashboardDefinitionDto updatedDashboard) {
    return getRequestExecutor()
      .buildUpdateDashboardRequest(id, updatedDashboard)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public Response updateDashboardAsUser(String id, DashboardDefinitionDto updatedDashboard, String username,
                                        String password) {
    return getRequestExecutor()
      .buildUpdateDashboardRequest(id, updatedDashboard)
      .withUserAuthentication(username, password)
      .execute();
  }

  public IdDto copyDashboard(final String dashboardId) {
    return copyDashboardToCollection(dashboardId, null);
  }

  public IdDto copyDashboardToCollection(final String dashboardId, final String collectionId) {
    return getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId, collectionId)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());
  }

  public Response copyDashboardToCollectionAsUserAndGetRawResponse(final String dashboardId,
                                                                   final String collectionId, String username,
                                                                   String password) {
    return getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId, collectionId)
      .withUserAuthentication(username, password)
      .execute();
  }

  public void deleteDashboard(final String dashboardId) {
    deleteDashboard(dashboardId, false);
  }

  public void deleteDashboard(final String dashboardId, final boolean force) {
    getRequestExecutor()
      .buildDeleteDashboardRequest(dashboardId, force)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public Response deleteDashboardAsUser(final String dashboardId, String username, String password,
                                        final boolean force) {
    return getRequestExecutor()
      .buildDeleteDashboardRequest(dashboardId, force)
      .withUserAuthentication(username, password)
      .execute();
  }

  public String createDashboardShareForDashboard(final String dashboardId) {
    DashboardShareDto sharingDto = new DashboardShareDto();
    sharingDto.setDashboardId(dashboardId);
    return getRequestExecutor()
      .buildShareDashboardRequest(sharingDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode()).getId();
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

  public void assertDashboardIsDeleted(final String dashboardIdToDelete) {
    getRequestExecutor()
      .buildGetDashboardRequest(dashboardIdToDelete)
      .execute(Response.Status.NOT_FOUND.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
