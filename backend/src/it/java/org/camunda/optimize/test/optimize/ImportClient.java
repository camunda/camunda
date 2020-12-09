/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;

import javax.ws.rs.core.Response;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class ImportClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public Response importReport(final ReportDefinitionExportDto exportedDto) {
    return importReportAsUser(DEFAULT_USERNAME, DEFAULT_USERNAME, exportedDto);
  }

  public Response importReportAsUser(final String userId,
                                     final String password,
                                     final ReportDefinitionExportDto exportedDto) {
    return importReportIntoCollectionAsUser(userId, password, null, exportedDto);
  }

  public Response importReportIntoCollection(final String collectionId,
                                             final ReportDefinitionExportDto exportedDto) {
    return importReportIntoCollectionAsUser(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      collectionId,
      exportedDto
    );
  }

  public Response importReportIntoCollectionAsUser(final String userId,
                                                   final String password,
                                                   final String collectionId,
                                                   final ReportDefinitionExportDto exportedDto) {
    final HashSet<OptimizeEntityExportDto> exportedDtos = new HashSet<>();
    exportedDtos.add(exportedDto);
    return importEntityIntoCollectionAsUser(userId, password, collectionId, exportedDtos);
  }

  public Response importEntityIntoCollectionAsUser(final String userId,
                                                   final String password,
                                                   final String collectionId,
                                                   final Set<OptimizeEntityExportDto> exportedDtos) {
    return getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildImportEntityRequest(collectionId, exportedDtos)
      .execute();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
