/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service.incident;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.CompletedIncidentElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.incident.CompletedIncidentWriter;

import java.util.List;

@Slf4j
public class CompletedIncidentImportService extends AbstractIncidentImportService {

  private final CompletedIncidentWriter completedIncidentWriter;

  public CompletedIncidentImportService(CompletedIncidentWriter completedIncidentWriter,
                                        ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                        EngineContext engineContext) {
    super(elasticsearchImportJobExecutor, engineContext);
    this.completedIncidentWriter = completedIncidentWriter;
  }

  protected ElasticsearchImportJob<IncidentDto> createElasticsearchImportJob(List<IncidentDto> incidents,
                                                                             Runnable callback) {
    CompletedIncidentElasticsearchImportJob incidentImportJob =
      new CompletedIncidentElasticsearchImportJob(completedIncidentWriter, callback);
    incidentImportJob.setEntitiesToImport(incidents);
    return incidentImportJob;
  }

}
