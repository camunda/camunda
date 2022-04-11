/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class SharingClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public DashboardDefinitionRestDto evaluateDashboard(final String dashboardShareId) {
    return getRequestExecutor()
      .buildEvaluateSharedDashboardRequest(dashboardShareId)
      .execute(DashboardDefinitionRestDto.class, Response.Status.OK.getStatusCode());
  }

  public Response getDashboardShareResponse(final String dashboardId) {
    return getRequestExecutor()
      .buildFindShareForDashboardRequest(dashboardId)
      .execute();
  }

  public ReportDefinitionDto<?> evaluateReportForSharedDashboard(final String dashboardShareId,
                                                                 final String reportId) {
    return getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId)
      .execute(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public Response createDashboardShareResponse(DashboardShareRestDto share) {
    return createDashboardShareResponse(share, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response createDashboardShareResponse(DashboardShareRestDto share, String username, String password) {
    return getRequestExecutor()
      .buildShareDashboardRequest(share)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response createReportShareResponse(ReportShareRestDto share) {
    return getRequestExecutor()
      .buildShareReportRequest(share)
      .execute();
  }

  public String shareReport(final String reportId) {
    ReportShareRestDto shareDto = new ReportShareRestDto();
    shareDto.setReportId(reportId);
    return getRequestExecutor()
      .buildShareReportRequest(shareDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public String shareDashboard(final String dashboardId) {
    DashboardShareRestDto dashboardShare = new DashboardShareRestDto();
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
