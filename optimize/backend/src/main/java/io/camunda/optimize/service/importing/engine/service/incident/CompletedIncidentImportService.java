/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.engine.service.incident;

import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.incident.CompletedIncidentWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import io.camunda.optimize.service.importing.job.CompletedIncidentDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompletedIncidentImportService extends AbstractEngineIncidentImportService {

  private final CompletedIncidentWriter completedIncidentWriter;

  public CompletedIncidentImportService(
      final ConfigurationService configurationService,
      final CompletedIncidentWriter completedIncidentWriter,
      final EngineContext engineContext,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final DatabaseClient databaseClient) {
    super(configurationService, engineContext, processDefinitionResolverService, databaseClient);
    this.completedIncidentWriter = completedIncidentWriter;
  }

  @Override
  protected DatabaseImportJob<IncidentDto> createDatabaseImportJob(
      final List<IncidentDto> incidents, final Runnable callback) {
    final CompletedIncidentDatabaseImportJob incidentImportJob =
        new CompletedIncidentDatabaseImportJob(
            completedIncidentWriter, configurationService, callback, databaseClient);
    incidentImportJob.setEntitiesToImport(incidents);
    return incidentImportJob;
  }
}
