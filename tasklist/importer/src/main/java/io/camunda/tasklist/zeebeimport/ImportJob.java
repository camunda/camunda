/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.webapps.schema.entities.ImportPositionEntity;
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
