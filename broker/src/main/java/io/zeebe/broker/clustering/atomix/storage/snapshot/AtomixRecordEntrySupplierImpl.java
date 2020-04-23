/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.storage.journal.Indexed;
import io.zeebe.broker.clustering.atomix.storage.AtomixRecordEntrySupplier;
import io.zeebe.logstreams.storage.atomix.ZeebeIndexMapping;
import java.util.Optional;

public final class AtomixRecordEntrySupplierImpl implements AtomixRecordEntrySupplier {
  private final ZeebeIndexMapping indexMapping;
  private final RaftLogReader reader;

  public AtomixRecordEntrySupplierImpl(
      final ZeebeIndexMapping indexMapping, final RaftLogReader reader) {
    this.indexMapping = indexMapping;
    this.reader = reader;
  }

  @Override
  public Optional<Indexed<? extends RaftLogEntry>> getIndexedEntry(final long position) {
    final var index = indexMapping.lookupPosition(position);
    if (index == -1) {
      return Optional.empty();
    }

    reader.reset(index - 1);
    if (reader.hasNext()) {
      final var indexedEntry = reader.next();
      if (indexedEntry.index() < index) {
        return Optional.of(indexedEntry);
      }
    }

    return Optional.empty();
  }

  @Override
  public void close() {
    reader.close();
  }
}
