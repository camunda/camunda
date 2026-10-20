/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.camunda.zeebe.eventplane.publisher.EntrySource;
import io.camunda.zeebe.eventplane.publisher.SourcedEntry;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import org.jspecify.annotations.Nullable;

/**
 * A partition's committed journal, from Raft's committed reader. The entries are the journal's own
 * bytes, and the records' timestamps come from the application entries.
 */
final class RaftEntrySource implements EntrySource {

  private final RaftLogReader reader;
  private final LoggedEventImpl event = new LoggedEventImpl();

  RaftEntrySource(final RaftLogReader reader) {
    this.reader = reader;
  }

  @Override
  public long firstIndex() {
    return reader.reset();
  }

  @Override
  public void seek(final long index) {
    reader.seek(index);
  }

  @Override
  public @Nullable SourcedEntry next() {
    if (!reader.hasNext()) {
      return null;
    }
    final var indexed = reader.next();
    final var record = indexed.getReplicatableJournalRecord();
    final var entry = new Entry(record.checksum(), record.serializedJournalRecord());
    if (!indexed.isApplicationEntry()) {
      return new SourcedEntry(entry, Entry.NO_POSITION);
    }
    // application entries read from the log are always serialized ones
    final var data = ((SerializedApplicationEntry) indexed.getApplicationEntry()).data();
    long latest = 0;
    for (int offset = 0; offset < data.capacity(); offset += event.getLength()) {
      event.wrap(data, offset);
      latest = Math.max(latest, event.getTimestamp());
    }
    return new SourcedEntry(entry, latest);
  }

  @Override
  public void close() {
    reader.close();
  }
}
