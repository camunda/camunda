/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Callable;

public interface ImportJob extends Callable<Boolean> {

  public void refreshZeebeIndices();

  public void recordLatestScheduledPosition();

  public ImportPositionEntity getLastProcessedPosition();

  public boolean indexChange();

  public OffsetDateTime getCreationTime();

  public void processPossibleIndexChange();

  public List<ImportBatch> createSubBatchesPerIndexName();
}
