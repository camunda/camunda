/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.incident.OpenIncidentWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.ArrayList;
import java.util.List;

public class OpenIncidentElasticsearchImportJob extends ElasticsearchImportJob<IncidentDto> {

  private final OpenIncidentWriter openIncidentWriter;
  private final ConfigurationService configurationService;

  public OpenIncidentElasticsearchImportJob(final OpenIncidentWriter openIncidentWriter,
                                            final ConfigurationService configurationService,
                                            final Runnable callback) {
    super(callback);
    this.openIncidentWriter = openIncidentWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<IncidentDto> newOptimizeEntities) {
    final List<ImportRequestDto> importRequests =
      new ArrayList<>(openIncidentWriter.generateIncidentImports(newOptimizeEntities));
    ElasticsearchWriterUtil.executeImportRequestsAsBulk(
      "Open incidents",
      importRequests,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

}
