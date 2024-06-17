/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ImportIndexWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import java.util.List;

public class StoreIndexesDatabaseImportJob extends DatabaseImportJob<EngineImportIndexDto> {

  private final ImportIndexWriter importIndexWriter;

  public StoreIndexesDatabaseImportJob(
      final ImportIndexWriter importIndexWriter,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.importIndexWriter = importIndexWriter;
  }

  @Override
  protected void persistEntities(List<EngineImportIndexDto> newOptimizeEntities) {
    importIndexWriter.importIndexes(newOptimizeEntities);
  }
}
