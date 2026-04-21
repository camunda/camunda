/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.importing.ReportingMetricsDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ReportingMetricsWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class ReportingMetricsDatabaseImportJob extends DatabaseImportJob<ReportingMetricsDto> {

  private final ReportingMetricsWriter reportingMetricsWriter;
  private final ConfigurationService configurationService;

  public ReportingMetricsDatabaseImportJob(
      final ReportingMetricsWriter reportingMetricsWriter,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.reportingMetricsWriter = reportingMetricsWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<ReportingMetricsDto> metricsDocuments) {
    final List<ImportRequestDto> importRequests =
        reportingMetricsWriter.generateImports(metricsDocuments);
    databaseClient.executeImportRequestsAsBulk(
        "Zeebe reporting metrics",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
