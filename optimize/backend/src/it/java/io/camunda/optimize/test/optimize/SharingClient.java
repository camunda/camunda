/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;

public class SharingClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public SharingClient(final Supplier<OptimizeRequestExecutor> requestExecutorSupplier) {
    this.requestExecutorSupplier = requestExecutorSupplier;
  }

  public DashboardDefinitionRestDto evaluateDashboard(final String dashboardShareId) {
    return getRequestExecutor()
        .buildEvaluateSharedDashboardRequest(dashboardShareId)
        .execute(DashboardDefinitionRestDto.class, Response.Status.OK.getStatusCode());
  }

  public Response getDashboardShareResponse(final String dashboardId) {
    return getRequestExecutor().buildFindShareForDashboardRequest(dashboardId).execute();
  }

  public ReportDefinitionDto<?> evaluateReportForSharedDashboard(
      final String dashboardShareId, final String reportId) {
    return getRequestExecutor()
        .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId)
        .execute(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public Response createDashboardShareResponse(final DashboardShareRestDto share) {
    return createDashboardShareResponse(share, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response createDashboardShareResponse(
      final DashboardShareRestDto share, final String username, final String password) {
    return getRequestExecutor()
        .buildShareDashboardRequest(share)
        .withUserAuthentication(username, password)
        .execute();
  }

  public Response createReportShareResponse(final ReportShareRestDto share) {
    return getRequestExecutor().buildShareReportRequest(share).execute();
  }

  public String shareReport(final String reportId) {
    final ReportShareRestDto shareDto = new ReportShareRestDto();
    shareDto.setReportId(reportId);
    return getRequestExecutor()
        .buildShareReportRequest(shareDto)
        .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
        .getId();
  }

  public String shareDashboard(final String dashboardId) {
    final DashboardShareRestDto dashboardShare = new DashboardShareRestDto();
    dashboardShare.setDashboardId(dashboardId);
    return getRequestExecutor()
        .buildShareDashboardRequest(dashboardShare)
        .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
        .getId();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
