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
import org.camunda.optimize.service.es.writer.incident.OpenIncidentWriter;

import java.util.ArrayList;
import java.util.List;

public class OpenIncidentElasticsearchImportJob extends ElasticsearchImportJob<IncidentDto> {

  private final OpenIncidentWriter openIncidentWriter;

  public OpenIncidentElasticsearchImportJob(OpenIncidentWriter openIncidentWriter,
                                            Runnable callback) {
    super(callback);
    this.openIncidentWriter = openIncidentWriter;
  }

  @Override
  protected void persistEntities(List<IncidentDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests =
      new ArrayList<>(openIncidentWriter.generateIncidentImports(newOptimizeEntities));
    ElasticsearchWriterUtil.executeImportRequestsAsBulk("Open incidents", importRequests);
  }

}
