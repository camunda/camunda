/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.raft.protocol.PersistedRaftRecord;
import io.atomix.raft.protocol.ReplicatedJournalRecord;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.RaftEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;

public class TestIndexedRaftLogEntry implements IndexedRaftLogEntry {

  private final long index;
  private final long term;
  private final RaftEntry entry;

  public TestIndexedRaftLogEntry(final long index, final long term, final RaftEntry entry) {
    this.index = index;
    this.term = term;
    this.entry = entry;
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public long term() {
    return term;
  }

  @Override
  public RaftEntry entry() {
    return entry;
  }

  @Override
  public boolean isApplicationEntry() {
    return entry instanceof SerializedApplicationEntry;
  }

  @Override
  public ApplicationEntry getApplicationEntry() {
    return (ApplicationEntry) entry;
  }

  @Override
  public PersistedRaftRecord getPersistedRaftRecord() {
    return null;
  }

  @Override
  public ReplicatedJournalRecord getReplicatedJournalRecord() {
    return null;
  }
}
