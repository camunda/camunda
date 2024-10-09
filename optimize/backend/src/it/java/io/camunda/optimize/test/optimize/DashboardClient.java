/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DashboardClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public DashboardClient(final Supplier<OptimizeRequestExecutor> requestExecutorSupplier) {
    this.requestExecutorSupplier = requestExecutorSupplier;
  }

  public DashboardDefinitionRestDto getDashboard(final String dashboardId) {
    return getRequestExecutor()
        .buildGetDashboardRequest(dashboardId)
        .execute(DashboardDefinitionRestDto.class, Response.Status.OK.getStatusCode());
  }

  public DashboardDefinitionRestDto getManagementDashboard() {
    return getManagementDashboardLocalized(null);
  }

  public DashboardDefinitionRestDto getManagementDashboardLocalized(final String locale) {
    final OptimizeRequestExecutor requestExecutor = getRequestExecutor();
    Optional.ofNullable(locale)
        .ifPresent(loc -> requestExecutor.addSingleHeader(X_OPTIMIZE_CLIENT_LOCALE, locale));
    return requestExecutor
        .buildGetManagementDashboardRequest()
        .execute(DashboardDefinitionRestDto.class, Response.Status.OK.getStatusCode());
  }

  public DashboardDefinitionRestDto getInstantPreviewDashboard(
      final String processDefinitionKey, final String template) {
    return getInstantPreviewDashboardLocalized(processDefinitionKey, template, "en");
  }

  public DashboardDefinitionRestDto getInstantPreviewDashboardLocalized(
      final String processDefinitionKey, final String template, final String locale) {
    final OptimizeRequestExecutor requestExecutor = getRequestExecutor();
    Optional.ofNullable(locale)
        .ifPresent(loc -> requestExecutor.addSingleHeader(X_OPTIMIZE_CLIENT_LOCALE, locale));
    return requestExecutor
        .buildGetInstantPreviewDashboardRequest(processDefinitionKey, template)
        .execute(DashboardDefinitionRestDto.class, Response.Status.OK.getStatusCode());
  }

  public AuthorizedDashboardDefinitionResponseDto getDashboardAsUser(
      final String dashboardId, final String username, final String password) {
    return getRequestExecutor()
        .buildGetDashboardRequest(dashboardId)
        .withUserAuthentication(username, password)
        .execute(
            AuthorizedDashboardDefinitionResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public String createEmptyDashboard() {
    return createEmptyDashboard(null);
  }

  public String createEmptyDashboard(final String collectionId) {
    return createDashboard(collectionId, Collections.emptyList());
  }

  public String createDashboard(final String collectionId, final List<String> reportIds) {
    return createDashboard(createSimpleDashboardDefinition(collectionId, reportIds));
  }

  public Response createDashboardAsUserGetRawResponse(
      final String collectionId,
      final List<String> reportIds,
      final String username,
      final String password) {
    return getRequestExecutor()
        .buildCreateDashboardRequest(createSimpleDashboardDefinition(collectionId, reportIds))
        .withUserAuthentication(username, password)
        .execute();
  }

  public String createDashboardAsUser(
      final DashboardDefinitionRestDto dashboardDefinitionDto,
      final String username,
      final String password) {
    return getRequestExecutor()
        .buildCreateDashboardRequest(dashboardDefinitionDto)
        .withUserAuthentication(username, password)
        .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
        .getId();
  }

  public String createDashboard(final DashboardDefinitionRestDto dashboardDefinitionDto) {
    return getRequestExecutor()
        .buildCreateDashboardRequest(dashboardDefinitionDto)
        .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
        .getId();
  }

  public Response createDashboardAndReturnResponse(
      final DashboardDefinitionRestDto dashboardDefinitionDto) {
    return getRequestExecutor().buildCreateDashboardRequest(dashboardDefinitionDto).execute();
  }

  public Response updateDashboardWithReports(
      final String dashboardId, final List<String> reportIds) {
    final List<DashboardReportTileDto> reports =
        reportIds.stream()
            .map(
                reportId -> {
                  final DashboardReportTileDto dashboardTileDto = new DashboardReportTileDto();
                  dashboardTileDto.setId(reportId);
                  dashboardTileDto.setType(DashboardTileType.OPTIMIZE_REPORT);
                  return dashboardTileDto;
                })
            .collect(Collectors.toList());
    final DashboardDefinitionRestDto dashboard = new DashboardDefinitionRestDto();
    dashboard.setTiles(reports);
    return updateDashboard(dashboardId, dashboard);
  }

  public Response updateDashboard(
      final String id, final DashboardDefinitionRestDto updatedDashboard) {
    return getRequestExecutor()
        .buildUpdateDashboardRequest(id, updatedDashboard)
        .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public Response updateDashboardAndReturnResponse(
      final String id, final DashboardDefinitionRestDto updatedDashboard) {
    return getRequestExecutor().buildUpdateDashboardRequest(id, updatedDashboard).execute();
  }

  public Response updateDashboardAsUser(
      final String id,
      final DashboardDefinitionRestDto updatedDashboard,
      final String username,
      final String password) {
    return getRequestExecutor()
        .buildUpdateDashboardRequest(id, updatedDashboard)
        .withUserAuthentication(username, password)
        .execute();
  }

  public IdResponseDto copyDashboard(final String dashboardId) {
    return copyDashboardToCollection(dashboardId, null);
  }

  public Response copyDashboardAndReturnResponse(final String dashboardId) {
    return getRequestExecutor().buildCopyDashboardRequest(dashboardId, null).execute();
  }

  public IdResponseDto copyDashboardToCollection(
      final String dashboardId, final String collectionId) {
    return getRequestExecutor()
        .buildCopyDashboardRequest(dashboardId, collectionId)
        .execute(IdResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public Response copyDashboardToCollectionAsUserAndGetRawResponse(
      final String dashboardId,
      final String collectionId,
      final String username,
      final String password) {
    return getRequestExecutor()
        .buildCopyDashboardRequest(dashboardId, collectionId)
        .withUserAuthentication(username, password)
        .execute();
  }

  public void deleteDashboard(final String dashboardId) {
    deleteDashboard(dashboardId, false);
  }

  public Response deleteDashboardAndReturnResponse(final String dashboardId) {
    return getRequestExecutor().buildDeleteDashboardRequest(dashboardId, false).execute();
  }

  public void deleteDashboard(final String dashboardId, final boolean force) {
    getRequestExecutor()
        .buildDeleteDashboardRequest(dashboardId, force)
        .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public Response deleteDashboardAsUser(
      final String dashboardId, final String username, final String password, final boolean force) {
    return getRequestExecutor()
        .buildDeleteDashboardRequest(dashboardId, force)
        .withUserAuthentication(username, password)
        .execute();
  }

  public String createDashboardShareForDashboard(final String dashboardId) {
    final DashboardShareRestDto sharingDto = new DashboardShareRestDto();
    sharingDto.setDashboardId(dashboardId);
    return getRequestExecutor()
        .buildShareDashboardRequest(sharingDto)
        .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
        .getId();
  }

  private DashboardDefinitionRestDto createSimpleDashboardDefinition(
      final String collectionId, final List<String> reportIds) {
    final DashboardDefinitionRestDto definitionDto = new DashboardDefinitionRestDto();
    definitionDto.setName("MyAwesomeDashboard");
    definitionDto.setCollectionId(collectionId);
    definitionDto.setTiles(
        reportIds.stream()
            .map(
                reportId ->
                    DashboardReportTileDto.builder()
                        .id(reportId)
                        .type(DashboardTileType.OPTIMIZE_REPORT)
                        .build())
            .collect(Collectors.toList()));
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
