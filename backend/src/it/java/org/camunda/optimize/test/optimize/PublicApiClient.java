/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto.QUERY_LIMIT_PARAM;
import static org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto.QUERY_SCROLL_ID_PARAM;
import static org.camunda.optimize.rest.PublicApiRestService.QUERY_PARAMETER_ACCESS_TOKEN;

@AllArgsConstructor
public class PublicApiClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public List<ReportDefinitionExportDto> exportReportDefinitionsAndReturnResponse(final List<String> reportIds,
                                                                                  final String accessToken) {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildPublicExportJsonReportDefinitionRequest(reportIds, accessToken)
      .executeAndReturnList(ReportDefinitionExportDto.class, Response.Status.OK.getStatusCode());
  }

  public Response exportReportDefinitions(final List<String> reportIds,
                                          final String accessToken) {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildPublicExportJsonReportDefinitionRequest(reportIds, accessToken)
      .execute();
  }


  public Response exportDashboardDefinitions(final List<String> dashboardIds, final String accessToken) {
    return getRequestExecutor()
      .buildPublicExportJsonDashboardDefinitionRequest(dashboardIds, accessToken)
      .withoutAuthentication()
      .execute();
  }

  public List<OptimizeEntityExportDto> exportDashboardsAndReturnExportDtos(final List<String> dashboardIds,
                                                                           final String accessToken) {
    return getRequestExecutor()
      .buildPublicExportJsonDashboardDefinitionRequest(dashboardIds, accessToken)
      .withoutAuthentication()
      .executeAndReturnList(OptimizeEntityExportDto.class, Response.Status.OK.getStatusCode());
  }

  public Response importEntityAndReturnResponse(final Set<OptimizeEntityExportDto> exportedDtos,
                                                final String collectionId,
                                                final String accessToken) {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildPublicImportEntityDefinitionsRequest(collectionId, Sets.newHashSet(exportedDtos), accessToken)
      .execute();
  }

  public List<IdResponseDto> importEntityAndReturnIds(final Set<OptimizeEntityExportDto> exportedDtos,
                                                      final String collectionId,
                                                      final String accessToken) {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildPublicImportEntityDefinitionsRequest(collectionId, Sets.newHashSet(exportedDtos), accessToken)
      .executeAndReturnList(IdResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public Response deleteReport(final String reportId, final String accessToken) {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildPublicDeleteReportRequest(reportId, accessToken)
      .execute();
  }

  public Response deleteDashboard(final String dashboardId, final String accessToken) {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildPublicDeleteDashboardRequest(dashboardId, accessToken)
      .execute();
  }

  public Response exportReport(final String reportId, final String accessToken, final Integer limit,
                               final String searchRequestId) {
    return getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, accessToken)
      .addSingleQueryParam(QUERY_LIMIT_PARAM, limit)
      .addSingleQueryParam(QUERY_SCROLL_ID_PARAM, searchRequestId)
      .buildPublicExportJsonReportResultRequest(reportId)
      .withoutAuthentication()
      .execute();
  }

  public List<IdResponseDto> getAllReportIdsInCollection(final String collectionId, final String accessToken) {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildPublicGetAllReportIdsInCollectionRequest(collectionId, accessToken)
      .executeAndReturnList(IdResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<IdResponseDto> getAllDashboardIdsInCollection(final String collectionId, final String accessToken) {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildPublicGetAllDashboardIdsInCollectionRequest(collectionId, accessToken)
      .executeAndReturnList(IdResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
