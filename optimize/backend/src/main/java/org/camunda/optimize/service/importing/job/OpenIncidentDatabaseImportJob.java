/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import java.util.ArrayList;
import java.util.List;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.incident.OpenIncidentWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

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
  protected void persistEntities(List<IncidentDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests =
        new ArrayList<>(openIncidentWriter.generateIncidentImports(newOptimizeEntities));
    databaseClient.executeImportRequestsAsBulk(
        "Open incidents",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
