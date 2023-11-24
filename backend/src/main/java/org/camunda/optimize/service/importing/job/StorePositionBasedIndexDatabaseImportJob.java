/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.db.writer.PositionBasedImportIndexWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

import java.util.List;

public class StorePositionBasedIndexDatabaseImportJob extends DatabaseImportJob<PositionBasedImportIndexDto> {

  private final PositionBasedImportIndexWriter positionBasedImportIndexWriter;

  public StorePositionBasedIndexDatabaseImportJob(final PositionBasedImportIndexWriter positionBasedImportIndexWriter,
                                                  final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.positionBasedImportIndexWriter = positionBasedImportIndexWriter;
  }

  @Override
  protected void persistEntities(List<PositionBasedImportIndexDto> importIndices) {
    positionBasedImportIndexWriter.importIndexes(importIndices);
  }

}
