/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import java.util.List;
import org.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.ImportIndexWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

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
