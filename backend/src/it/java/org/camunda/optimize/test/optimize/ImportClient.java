/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.rest.export.SingleProcessReportDefinitionExportDto;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class ImportClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public Response importProcessReport(final SingleProcessReportDefinitionExportDto exportedDto) {
    return importProcessReportIntoCollectionAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD, null, exportedDto);
  }

  public Response importProcessReportAsUser(final String userId,
                                            final String password,
                                            final SingleProcessReportDefinitionExportDto exportedDto) {
    return importProcessReportIntoCollectionAsUser(userId, password, null, exportedDto);
  }

  public Response importProcessReportIntoCollection(final String collectionId,
                                                    final SingleProcessReportDefinitionExportDto exportedDto) {
    return importProcessReportIntoCollectionAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD, collectionId, exportedDto);
  }

  public Response importProcessReportIntoCollectionAsUser(final String userId,
                                                          final String password,
                                                          final String collectionId,
                                                          final SingleProcessReportDefinitionExportDto exportedDto) {
    return getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildImportProcessReportRequest(collectionId, exportedDto)
      .execute();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
