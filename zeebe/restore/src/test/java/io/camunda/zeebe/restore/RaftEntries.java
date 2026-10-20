/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A broker's journal entries, written the way Raft writes them, so that tests get the bytes a
 * publisher uploads. Each application entry holds two records, so that the n-th one holds the
 * positions {@code 2n - 1} and {@code 2n}, counting from where the journal's positions start.
 */
final class RaftEntries implements AutoCloseable {

  private final SegmentedJournal journal;
  private final RaftEntrySBESerializer serializer = new RaftEntrySBESerializer();
  private final Random random;
  private final List<Entry> written = new ArrayList<>();
  private long nextPosition = 1;

  RaftEntries(final Path directory, final long seed) {
    random = new Random(seed);
    journal =
        SegmentedJournal.builder(new SimpleMeterRegistry())
            .withDirectory(directory.toFile())
            .withMaxSegmentSize(4 << 20)
            .withFreeDiskSpace(0)
            .withMetaStore(new JournalMetaStore.InMemory())
            .build();
  }

  /** What a new leader appends first: an entry without records. */
  Entry initial() {
    return append(SegmentedJournal.ASQN_IGNORE, new InitialEntry().toSerializable(1, serializer));
  }

  /** An application entry of two records, with random data. */
  Entry records() {
    final var data = new byte[64];
    random.nextBytes(data);
    final long lowest = nextPosition;
    nextPosition += 2;
    final var entry =
        new SerializedApplicationEntry(lowest, nextPosition - 1, ByteBuffer.wrap(data));
    return append(lowest, entry.toSerializable(1, serializer));
  }

  /** Appends entries of another journal, as a journal that took them over has them. */
  void takeOver(final List<Entry> entries) {
    for (final var entry : entries) {
      journal.append(entry.checksum(), entry.serializedRecord());
      written.add(entry);
      if (entry.hasRecords()) {
        nextPosition = entry.lastPosition() + 1;
      }
    }
  }

  /** Every entry so far; entry {@code i} is at {@code i - 1}. */
  List<Entry> entries() {
    return List.copyOf(written);
  }

  /** The entries with these indexes, both included. */
  List<Entry> entries(final long firstIndex, final long lastIndex) {
    return written.subList((int) firstIndex - 1, (int) lastIndex);
  }

  private Entry append(final long asqn, final BufferWriter writer) {
    final var record = journal.append(asqn, writer);
    final var bytes = new byte[record.serializedRecord().capacity()];
    record.serializedRecord().getBytes(0, bytes);
    final var entry = new Entry(record.checksum(), bytes);
    written.add(entry);
    return entry;
  }

  @Override
  public void close() {
    journal.close();
  }
}
