/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.importing.StoreIndexesElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;

/**
 * Write all information of the current import index to elasticsearch.
 * If Optimize is restarted the import index can thus be restored again.
 */
@Slf4j
public class StoreIndexesEngineImportService implements ImportService<EngineImportIndexDto> {
  private final ImportIndexWriter importIndexWriter;
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  public StoreIndexesEngineImportService(final ConfigurationService configurationService,
                                         final ImportIndexWriter importIndexWriter) {
    this.elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.importIndexWriter = importIndexWriter;
  }

  public void executeImport(final List<EngineImportIndexDto> importIndexesToStore, final Runnable importCompleteCallback) {
    final StoreIndexesElasticsearchImportJob storeIndexesImportJob = new StoreIndexesElasticsearchImportJob(
      importIndexWriter, importCompleteCallback
    );
    storeIndexesImportJob.setEntitiesToImport(importIndexesToStore);
    elasticsearchImportJobExecutor.executeImportJob(storeIndexesImportJob);
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

}
