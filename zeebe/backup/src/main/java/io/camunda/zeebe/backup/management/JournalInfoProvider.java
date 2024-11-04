/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Get the some information from the journal from an outside thread. All APIs are thread safe and
 * can be safely used from a different thread then the one used by Raft.
 */
public interface JournalInfoProvider {

  /**
   * Get the segment files that are after or equal to an index.
   *
   * <p>As an example, it can Useful for getting all the segment files >= commit_index or
   * checkpoint_index.
   *
   * @param index the index in the log to select the segments
   * @return a future containing a collection of segment files
   */
  CompletableFuture<Collection<Path>> getTailSegments(long index);
}
