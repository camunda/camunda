/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.journal.SegmentInfo;
import java.util.concurrent.CompletableFuture;

/**
 * Get some information from the journal from an outside thread. All APIs are thread safe and can be
 * safely used from a different thread then the one used by Raft.
 */
public interface JournalInfoProvider {

  /**
   * Get the segment files that are after or equal to an index.
   *
   * <p>As an example, it can be useful for getting all the segment files >= commit_index or
   * checkpoint_index.
   *
   * @param index the index in the log to select the segments
   * @return a future containing the tail segments with paths and the first ASQN
   */
  CompletableFuture<SegmentInfo> getTailSegments(long index);
}
