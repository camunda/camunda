/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.persistence.incident.FlatIncidentDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.IncidentWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class FlatIncidentDatabaseImportJob extends DatabaseImportJob<FlatIncidentDto> {

  private final IncidentWriter incidentWriter;
  private final ConfigurationService configurationService;

  public FlatIncidentDatabaseImportJob(
      final IncidentWriter incidentWriter,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.incidentWriter = incidentWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<FlatIncidentDto> incidents) {
    final List<ImportRequestDto> importRequests =
        incidentWriter.generateFlatIncidentImports(incidents);
    databaseClient.executeImportRequestsAsBulk(
        "flat incidents",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
