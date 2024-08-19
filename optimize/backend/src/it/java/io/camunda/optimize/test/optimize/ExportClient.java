/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

public class ExportClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public ExportClient(final Supplier<OptimizeRequestExecutor> requestExecutorSupplier) {
    this.requestExecutorSupplier = requestExecutorSupplier;
  }

  public Response exportReportAsCsv(final String reportId, final String fileName) {
    return getRequestExecutor().buildCsvExportRequest(reportId, fileName).execute();
  }

  public Response exportReportAsCsv(
      final String reportId, final String fileName, final String timezone) {
    return getRequestExecutor()
        .buildCsvExportRequest(reportId, fileName)
        .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, timezone)
        .execute();
  }

  public Response exportReportAsJsonAsDemo(final String reportId, final String fileName) {
    return getRequestExecutor().buildExportReportRequest(reportId, fileName).execute();
  }

  public List<ReportDefinitionExportDto> exportReportAsJsonAndReturnExportDtosAsDemo(
      final String reportId, final String fileName) {
    return getRequestExecutor()
        .buildExportReportRequest(reportId, fileName)
        .executeAndReturnList(ReportDefinitionExportDto.class, Response.Status.OK.getStatusCode());
  }

  public Response exportReportAsJsonAsUser(
      final String userId, final String password, final String reportId, final String fileName) {
    return getRequestExecutor()
        .withUserAuthentication(userId, password)
        .buildExportReportRequest(reportId, fileName)
        .execute();
  }

  public Response exportDashboardDefinitionAsDemo(final String dashboardId, final String fileName) {
    return getRequestExecutor().buildExportDashboardRequest(dashboardId, fileName).execute();
  }

  public List<OptimizeEntityExportDto> exportDashboardAndReturnExportDtosAsDemo(
      final String dashboardId, final String fileName) {
    return getRequestExecutor()
        .buildExportDashboardRequest(dashboardId, fileName)
        .executeAndReturnList(OptimizeEntityExportDto.class, Response.Status.OK.getStatusCode());
  }

  public Response exportDashboardAsUser(
      final String userId, final String password, final String dashboardId, final String fileName) {
    return getRequestExecutor()
        .withUserAuthentication(userId, password)
        .buildExportDashboardRequest(dashboardId, fileName)
        .execute();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
