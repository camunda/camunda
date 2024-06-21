/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.incident.CompletedIncidentWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.List;

public class CompletedIncidentDatabaseImportJob extends DatabaseImportJob<IncidentDto> {

  private final CompletedIncidentWriter completedIncidentWriter;
  private final ConfigurationService configurationService;

  public CompletedIncidentDatabaseImportJob(
      final CompletedIncidentWriter completedIncidentWriter,
      final ConfigurationService configurationService,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.completedIncidentWriter = completedIncidentWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<IncidentDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests =
        new ArrayList<>(completedIncidentWriter.generateIncidentImports(newOptimizeEntities));
    databaseClient.executeImportRequestsAsBulk(
        "Completed incidents",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
