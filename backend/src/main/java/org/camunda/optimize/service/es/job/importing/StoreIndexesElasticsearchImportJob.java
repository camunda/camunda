/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.importing.index.CombinedImportIndexesDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;

import java.util.List;

public class StoreIndexesElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeEventDto> {

  private ImportIndexWriter importIndexWriter;
  private CombinedImportIndexesDto indexesToStore;

  public StoreIndexesElasticsearchImportJob(ImportIndexWriter importIndexWriter, CombinedImportIndexesDto indexesToStore) {

    super(() -> {});
    this.importIndexWriter = importIndexWriter;
    this.indexesToStore = indexesToStore;
  }

  @Override
  protected void persistEntities(List<FlowNodeEventDto> newOptimizeEntities) throws Exception {
    importIndexWriter.importIndexes(indexesToStore);
  }
}
