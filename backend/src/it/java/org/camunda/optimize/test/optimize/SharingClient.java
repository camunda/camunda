/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class SharingClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public DashboardDefinitionDto evaluateDashboard(final String dashboardShareId) {
    return getRequestExecutor()
      .buildEvaluateSharedDashboardRequest(dashboardShareId)
      .execute(DashboardDefinitionDto.class, Response.Status.OK.getStatusCode());
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

  public Response createDashboardShareResponse(DashboardShareDto share) {
    return createDashboardShareResponse(share, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response createDashboardShareResponse(DashboardShareDto share, String username, String password) {
    return getRequestExecutor()
      .buildShareDashboardRequest(share)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response createReportShareResponse(ReportShareDto share) {
    return getRequestExecutor()
      .buildShareReportRequest(share)
      .execute();
  }


  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
