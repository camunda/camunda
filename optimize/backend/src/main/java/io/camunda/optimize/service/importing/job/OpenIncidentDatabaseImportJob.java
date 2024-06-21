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
import io.camunda.optimize.service.db.writer.incident.OpenIncidentWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.List;

public class OpenIncidentDatabaseImportJob extends DatabaseImportJob<IncidentDto> {

  private final OpenIncidentWriter openIncidentWriter;
  private final ConfigurationService configurationService;

  public OpenIncidentDatabaseImportJob(
      final OpenIncidentWriter openIncidentWriter,
      final ConfigurationService configurationService,
      final Runnable callback,
      final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.openIncidentWriter = openIncidentWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<IncidentDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests =
        new ArrayList<>(openIncidentWriter.generateIncidentImports(newOptimizeEntities));
    databaseClient.executeImportRequestsAsBulk(
        "Open incidents",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
