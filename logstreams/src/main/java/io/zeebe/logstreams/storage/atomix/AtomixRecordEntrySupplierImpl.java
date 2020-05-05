/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.raft.impl.zeebe.snapshot.AtomixRecordEntrySupplier;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.storage.journal.Indexed;
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
  public Optional<Indexed<RaftLogEntry>> getIndexedEntry(final long position) {
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
