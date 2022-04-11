/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.PositionBasedImportIndexWriter;

import java.util.List;

public class StorePositionBasedIndexElasticsearchImportJob extends ElasticsearchImportJob<PositionBasedImportIndexDto> {

  private final PositionBasedImportIndexWriter positionBasedImportIndexWriter;

  public StorePositionBasedIndexElasticsearchImportJob(final PositionBasedImportIndexWriter positionBasedImportIndexWriter,
                                                       final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.positionBasedImportIndexWriter = positionBasedImportIndexWriter;
  }

  @Override
  protected void persistEntities(List<PositionBasedImportIndexDto> importIndices) {
    positionBasedImportIndexWriter.importIndexes(importIndices);
  }
}
