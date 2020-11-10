/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.incident.CompletedIncidentWriter;

import java.util.ArrayList;
import java.util.List;

public class CompletedIncidentElasticsearchImportJob extends ElasticsearchImportJob<IncidentDto> {

  private final CompletedIncidentWriter completedIncidentWriter;

  public CompletedIncidentElasticsearchImportJob(CompletedIncidentWriter completedIncidentWriter,
                                                 Runnable callback) {
    super(callback);
    this.completedIncidentWriter = completedIncidentWriter;
  }

  @Override
  protected void persistEntities(List<IncidentDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests =
      new ArrayList<>(completedIncidentWriter.generateIncidentImports(newOptimizeEntities));
    ElasticsearchWriterUtil.executeImportRequestsAsBulk("Completed incidents", importRequests);
  }

}
