/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import java.util.Optional;

public final class AtomixRecordEntrySupplierImpl implements AtomixRecordEntrySupplier {
  private final RaftPartitionServer raftPartitionServer;

  public AtomixRecordEntrySupplierImpl(final RaftPartitionServer raftPartitionServer) {
    this.raftPartitionServer = raftPartitionServer;
  }

  @Override
  public Optional<IndexedRaftLogEntry> getPreviousIndexedEntry(final long position) {
    try (final var reader = raftPartitionServer.openReader()) {
      // Here we are seeking twice. Since this method is only called when taking a snapshot it is ok
      // to be not very efficient.
      final long recordIndex = reader.seekToAsqn(position);
      final long prevIndex = recordIndex - 1;
      if (reader.seek(prevIndex) == prevIndex) {
        return Optional.of(reader.next());
      }

      return Optional.empty();
    }
  }
}
