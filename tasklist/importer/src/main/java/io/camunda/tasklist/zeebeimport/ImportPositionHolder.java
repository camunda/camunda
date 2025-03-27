/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.tasklist.util.Either;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ImportPositionHolder {

  public void scheduleImportPositionUpdateTask();

  public CompletableFuture<Void> cancelScheduledImportPositionUpdateTask();

  public ImportPositionEntity getLatestScheduledPosition(String aliasTemplate, int partitionId)
      throws IOException;

  public void recordLatestScheduledPosition(
      String aliasName, int partitionId, ImportPositionEntity importPositionEntity);

  public ImportPositionEntity getLatestLoadedPosition(String aliasTemplate, int partitionId)
      throws IOException;

  public void recordLatestLoadedPosition(ImportPositionEntity lastProcessedPosition);

  public void clearCache();

  public Either<Throwable, Boolean> updateImportPositions(
      final Map<String, ImportPositionEntity> positions);

  public void updateImportPositions();
}
