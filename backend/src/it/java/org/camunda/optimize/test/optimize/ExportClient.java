/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;

@AllArgsConstructor
public class ExportClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public Response exportReportAsCsv(String reportId, String fileName) {
    return getRequestExecutor()
      .buildCsvExportRequest(reportId, fileName)
      .execute();
  }

  public Response exportReportAsJson(final String reportId, final String fileName) {
    return getRequestExecutor()
      .buildExportReportRequest(reportId, fileName)
      .execute();
  }

  public Response exportReportAsJsonAsUser(final String userId,
                                           final String password,
                                           final String reportId,
                                           final String fileName) {
    return getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildExportReportRequest(reportId, fileName)
      .execute();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
