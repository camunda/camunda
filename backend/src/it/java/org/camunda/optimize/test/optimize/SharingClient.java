/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.apache.http.HttpStatus;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;

@AllArgsConstructor
public class SharingClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public DashboardDefinitionDto evaluateDashboard(final String dashboardShareId) {
    return getRequestExecutor()
      .buildEvaluateSharedDashboardRequest(dashboardShareId)
      .execute(DashboardDefinitionDto.class, HttpStatus.SC_OK);
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
      .execute(ReportDefinitionDto.class, HttpStatus.SC_OK);
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
