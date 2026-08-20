/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.storage.system;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents information stored in the MetaStore with "Meta" SBE message (see
 * raft-entry-schema.xml)
 */
public record MetaStoreRecord(long term, long lastFlushedIndex, long commitIndex, String votedFor) {

  public MetaStoreRecord {
    checkArgument(term >= 0, "term must be >= 0, but was: %d", term);
    checkArgument(
        lastFlushedIndex >= -1,
        "lastFlushedIndex term must be >= -1, but was: %d",
        lastFlushedIndex);
    checkArgument(commitIndex >= -1, "commitIndex must be >= -1 , but was: ", commitIndex);
  }
}
