/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
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
import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * A broker's journal, written the way Raft writes it, so that tests get the bytes a publisher
 * reads. Each entry comes as the {@link SourcedEntry} a source would hand out.
 */
final class RaftJournal implements AutoCloseable {

  private static final int DATA_LENGTH = 32;

  private final SegmentedJournal journal;
  private final RaftEntrySBESerializer serializer = new RaftEntrySBESerializer();
  private final Random random = new Random(42);
  private long nextPosition = 1;

  RaftJournal(final Path directory) {
    journal =
        SegmentedJournal.builder(new SimpleMeterRegistry())
            .withDirectory(directory.toFile())
            .withMaxSegmentSize(4 << 20)
            .withFreeDiskSpace(0)
            .withMetaStore(new JournalMetaStore.InMemory())
            .build();
  }

  /** What a new leader appends first: an entry without records. */
  SourcedEntry initial() {
    return append(
        SegmentedJournal.ASQN_IGNORE,
        new InitialEntry().toSerializable(1, serializer),
        Entry.NO_POSITION);
  }

  /** Another entry without records. */
  SourcedEntry configuration() {
    final var member =
        new DefaultRaftMember(MemberId.from("0"), RaftMember.Type.ACTIVE, Instant.EPOCH);
    return append(
        SegmentedJournal.ASQN_IGNORE,
        new ConfigurationEntry(1, List.of(member)).toSerializable(1, serializer),
        Entry.NO_POSITION);
  }

  /**
   * An application entry of {@code count} records, whose latest was written at {@code timestamp}.
   */
  SourcedEntry records(final int count, final long timestamp) {
    final var data = new byte[DATA_LENGTH];
    random.nextBytes(data);
    final long lowest = nextPosition;
    nextPosition += count;
    final var entry =
        new SerializedApplicationEntry(lowest, nextPosition - 1, ByteBuffer.wrap(data));
    return append(lowest, entry.toSerializable(1, serializer), timestamp);
  }

  private SourcedEntry append(
      final long asqn, final BufferWriter writer, final long latestRecordTimestamp) {
    final var record = journal.append(asqn, writer);
    final var bytes = new byte[record.serializedRecord().capacity()];
    record.serializedRecord().getBytes(0, bytes);
    return new SourcedEntry(new Entry(record.checksum(), bytes), latestRecordTimestamp);
  }

  @Override
  public void close() {
    journal.close();
  }
}
