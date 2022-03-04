/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;

import java.util.List;

public class StoreIndexesElasticsearchImportJob extends ElasticsearchImportJob<EngineImportIndexDto> {

  private final ImportIndexWriter importIndexWriter;

  public StoreIndexesElasticsearchImportJob(final ImportIndexWriter importIndexWriter,
                                            final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.importIndexWriter = importIndexWriter;
  }

  @Override
  protected void persistEntities(List<EngineImportIndexDto> newOptimizeEntities) {
    importIndexWriter.importIndexes(newOptimizeEntities);
  }
}
