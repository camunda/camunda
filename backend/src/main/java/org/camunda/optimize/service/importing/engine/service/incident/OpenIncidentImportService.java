/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.incident;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.db.writer.incident.OpenIncidentWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.importing.job.OpenIncidentDatabaseImportJob;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;

@Slf4j
public class OpenIncidentImportService extends AbstractEngineIncidentImportService {

  private final OpenIncidentWriter openIncidentWriter;

  public OpenIncidentImportService(final ConfigurationService configurationService,
                                   final OpenIncidentWriter openIncidentWriter,
                                   final EngineContext engineContext,
                                   final ProcessDefinitionResolverService processDefinitionResolverService) {
    super(configurationService, engineContext, processDefinitionResolverService);
    this.openIncidentWriter = openIncidentWriter;
  }

  protected DatabaseImportJob<IncidentDto> createDatabaseImportJob(List<IncidentDto> incidents,
                                                                        Runnable callback) {
    OpenIncidentDatabaseImportJob incidentImportJob =
      new OpenIncidentDatabaseImportJob(openIncidentWriter, configurationService, callback);
    incidentImportJob.setEntitiesToImport(incidents);
    return incidentImportJob;
  }

}
