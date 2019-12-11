/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.apache.http.HttpStatus;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;

import javax.ws.rs.core.Response;

@AllArgsConstructor
public class SharingClient {

  private final EmbeddedOptimizeExtension embeddedOptimizeExtension;

  public DashboardDefinitionDto evaluateDashboard(final String dashboardShareId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardRequest(dashboardShareId)
      .execute(DashboardDefinitionDto.class, HttpStatus.SC_OK);
  }

  public Response getDashboardShareResponse(final String dashboardId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildFindShareForDashboardRequest(dashboardId)
      .execute();
  }

  public ReportDefinitionDto<?> evaluateReportForSharedDashboard(final String dashboardShareId,
                                                                 final String reportId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId)
      .execute(ReportDefinitionDto.class, HttpStatus.SC_OK);
  }
}
