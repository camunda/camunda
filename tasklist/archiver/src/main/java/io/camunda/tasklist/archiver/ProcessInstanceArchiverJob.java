/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ProcessInstanceArchiverJob extends Runnable {
  public CompletableFuture<Map.Entry<String, Integer>> archiveBatch(
      AbstractArchiverJob.ArchiveBatch archiveBatch);

  public CompletableFuture<AbstractArchiverJob.ArchiveBatch> getNextBatch();

  public CompletableFuture<Map.Entry<String, Integer>> archiveNextBatch();
}
