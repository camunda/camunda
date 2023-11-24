/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.db.writer.PositionBasedImportIndexWriter;
import org.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import org.camunda.optimize.service.importing.job.StorePositionBasedIndexDatabaseImportJob;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;

/**
 * Write all information of the current import index to elasticsearch.
 * If Optimize is restarted the import index can thus be restored again.
 */
@AllArgsConstructor
@Slf4j
public class StorePositionBasedIndexImportService implements ImportService<PositionBasedImportIndexDto> {

  private final PositionBasedImportIndexWriter importIndexWriter;
  private final DatabaseImportJobExecutor databaseImportJobExecutor;

  public StorePositionBasedIndexImportService(final ConfigurationService configurationService,
                                              final PositionBasedImportIndexWriter importIndexWriter) {
    this.databaseImportJobExecutor = new DatabaseImportJobExecutor(
      getClass().getSimpleName(), configurationService
    );
    this.importIndexWriter = importIndexWriter;
  }

  public void executeImport(final List<PositionBasedImportIndexDto> importIndexesToStore,
                            final Runnable importCompleteCallback) {
    final StorePositionBasedIndexDatabaseImportJob storeIndexesImportJob =
      new StorePositionBasedIndexDatabaseImportJob(
      importIndexWriter, importCompleteCallback
    );
    storeIndexesImportJob.setEntitiesToImport(importIndexesToStore);
    databaseImportJobExecutor.executeImportJob(storeIndexesImportJob);
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

}
