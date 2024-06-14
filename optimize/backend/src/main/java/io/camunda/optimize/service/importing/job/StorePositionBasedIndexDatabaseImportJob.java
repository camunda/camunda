/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.PositionBasedImportIndexWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import java.util.List;

public class StorePositionBasedIndexDatabaseImportJob
    extends DatabaseImportJob<PositionBasedImportIndexDto> {

  private final PositionBasedImportIndexWriter positionBasedImportIndexWriter;

  public StorePositionBasedIndexDatabaseImportJob(
      final PositionBasedImportIndexWriter positionBasedImportIndexWriter,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.positionBasedImportIndexWriter = positionBasedImportIndexWriter;
  }

  @Override
  protected void persistEntities(List<PositionBasedImportIndexDto> importIndices) {
    positionBasedImportIndexWriter.importIndexes(importIndices);
  }
}
