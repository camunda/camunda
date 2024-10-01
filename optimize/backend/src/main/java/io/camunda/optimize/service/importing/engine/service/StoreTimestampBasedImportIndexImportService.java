/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service;

import io.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ImportIndexWriter;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.job.StoreTimestampBasedImportIndexDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.slf4j.Logger;

/**
 * Write all information of the current import index to elasticsearch. If Optimize is restarted the
 * import index can thus be restored again.
 */
public class StoreTimestampBasedImportIndexImportService
    implements ImportService<TimestampBasedImportIndexDto> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(StoreTimestampBasedImportIndexImportService.class);

  private final ImportIndexWriter importIndexWriter;
  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final DatabaseClient databaseClient;

  public StoreTimestampBasedImportIndexImportService(
      final ConfigurationService configurationService,
      final ImportIndexWriter importIndexWriter,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.importIndexWriter = importIndexWriter;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<TimestampBasedImportIndexDto> importIndexesToStore,
      final Runnable importCompleteCallback) {
    final StoreTimestampBasedImportIndexDatabaseImportJob storeIndexesImportJob =
        new StoreTimestampBasedImportIndexDatabaseImportJob(
            importIndexWriter, importCompleteCallback, databaseClient);
    storeIndexesImportJob.setEntitiesToImport(importIndexesToStore);
    databaseImportJobExecutor.executeImportJob(storeIndexesImportJob);
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }
}
