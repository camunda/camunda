/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.protocol;

import io.atomix.cluster.MemberId;
import java.util.List;

/** Used by RaftRoles to handle AppendRequest from multiple versions uniformly. */
public record InternalAppendRequest(
    int version,
    long term,
    MemberId leader,
    long prevLogIndex,
    long prevLogTerm,
    long commitIndex,
    List<PersistedRaftRecord> raftRecords,
    List<ReplicatableJournalRecord> serializedJournalRecords) {
  static final int APPEND_REQUEST_WITH_RAFT_RECORDS = 1;

  public List<? extends ReplicatableRecord> entries() {
    return version == APPEND_REQUEST_WITH_RAFT_RECORDS ? raftRecords : serializedJournalRecords;
  }
}
