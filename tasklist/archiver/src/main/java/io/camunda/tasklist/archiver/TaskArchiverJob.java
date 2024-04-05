/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface TaskArchiverJob extends Runnable {
  public CompletableFuture<Map.Entry<String, Integer>> archiveBatch(
      AbstractArchiverJob.ArchiveBatch archiveBatch);

  public CompletableFuture<AbstractArchiverJob.ArchiveBatch> getNextBatch();

  public CompletableFuture<Map.Entry<String, Integer>> archiveNextBatch();
}
