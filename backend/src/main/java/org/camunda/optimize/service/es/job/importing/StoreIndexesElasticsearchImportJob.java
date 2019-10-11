/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.index.ImportIndexDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;

import java.util.List;

public class StoreIndexesElasticsearchImportJob extends ElasticsearchImportJob<ImportIndexDto> {

  private ImportIndexWriter importIndexWriter;

  public StoreIndexesElasticsearchImportJob(final ImportIndexWriter importIndexWriter) {
    super();
    this.importIndexWriter = importIndexWriter;
  }

  @Override
  protected void persistEntities(List<ImportIndexDto> newOptimizeEntities) {
    importIndexWriter.importIndexes(newOptimizeEntities);
  }
}
