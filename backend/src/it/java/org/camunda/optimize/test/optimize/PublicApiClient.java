/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto.QUERY_LIMIT_PARAM;
import static org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto.QUERY_SCROLL_ID_PARAM;

@AllArgsConstructor
public class PublicApiClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public List<ReportDefinitionExportDto> exportReportDefinitionsAndReturnResponse(final List<String> reportIds,
                                                                                  final String accessToken) {
    return getRequestExecutor()
      .buildPublicExportJsonReportDefinitionRequest(reportIds, accessToken)
      .executeAndReturnList(ReportDefinitionExportDto.class, Response.Status.OK.getStatusCode());
  }

  public Response exportReportDefinitions(final List<String> reportIds,
                                          final String accessToken) {
    return getRequestExecutor()
      .buildPublicExportJsonReportDefinitionRequest(reportIds, accessToken)
      .execute();
  }


  public Response exportDashboardDefinitions(final List<String> dashboardIds, final String accessToken) {
    return getRequestExecutor()
      .buildPublicExportJsonDashboardDefinitionRequest(dashboardIds, accessToken)
      .execute();
  }

  public List<OptimizeEntityExportDto> exportDashboardsAndReturnExportDtos(final List<String> dashboardIds,
                                                                           final String accessToken) {
    return getRequestExecutor()
      .buildPublicExportJsonDashboardDefinitionRequest(dashboardIds, accessToken)
      .executeAndReturnList(OptimizeEntityExportDto.class, Response.Status.OK.getStatusCode());
  }

  public Response importEntityAndReturnResponse(final Set<OptimizeEntityExportDto> exportedDtos,
                                                final String collectionId,
                                                final String accessToken) {
    return getRequestExecutor()
      .buildPublicImportEntityDefinitionsRequest(collectionId, Sets.newHashSet(exportedDtos), accessToken)
      .execute();
  }

  public List<EntityIdResponseDto> importEntityAndReturnIds(final Set<OptimizeEntityExportDto> exportedDtos,
                                                            final String collectionId,
                                                            final String accessToken) {
    return getRequestExecutor()
      .buildPublicImportEntityDefinitionsRequest(collectionId, Sets.newHashSet(exportedDtos), accessToken)
      .executeAndReturnList(EntityIdResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public Response deleteReport(final String reportId, final String accessToken) {
    return getRequestExecutor()
      .buildPublicDeleteReportRequest(reportId, accessToken)
      .execute();
  }

  public Response deleteDashboard(final String dashboardId, final String accessToken) {
    return getRequestExecutor()
      .buildPublicDeleteDashboardRequest(dashboardId, accessToken)
      .execute();
  }

  public Response exportReport(final String reportId, final String accessToken, final Integer limit,
                               final String searchRequestId) {
    return getRequestExecutor()
      .addSingleQueryParam(QUERY_LIMIT_PARAM, limit)
      .addSingleQueryParam(QUERY_SCROLL_ID_PARAM, searchRequestId)
      .buildPublicExportJsonReportResultRequest(reportId, accessToken)
      .execute();
  }

  public List<IdResponseDto> getAllReportIdsInCollection(final String collectionId, final String accessToken) {
    return getRequestExecutor()
      .buildPublicGetAllReportIdsInCollectionRequest(collectionId, accessToken)
      .executeAndReturnList(IdResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<IdResponseDto> getAllDashboardIdsInCollection(final String collectionId, final String accessToken) {
    return getRequestExecutor()
      .buildPublicGetAllDashboardIdsInCollectionRequest(collectionId, accessToken)
      .executeAndReturnList(IdResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public Response toggleSharing(boolean enableSharing, final String accessToken) {
    return getRequestExecutor()
      .buildToggleShareRequest(enableSharing, accessToken)
      .execute();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
