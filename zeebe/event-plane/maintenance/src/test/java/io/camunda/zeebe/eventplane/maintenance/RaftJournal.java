/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * A broker's journal, written the way Raft writes it, so that tests get the bytes a publisher
 * reads.
 */
final class RaftJournal implements AutoCloseable {

  private final Path directory;
  private final SegmentedJournal journal;
  private final RaftEntrySBESerializer serializer = new RaftEntrySBESerializer();
  private final Random random = new Random(42);
  private long nextPosition = 1;

  RaftJournal(Path directory) {
    this.directory = directory;
    journal =
        SegmentedJournal.builder(new SimpleMeterRegistry())
            .withDirectory(directory.toFile())
            .withMaxSegmentSize(4 << 20)
            .withFreeDiskSpace(0)
            .withMetaStore(new JournalMetaStore.InMemory())
            .build();
  }

  /** What a new leader appends first. */
  Entry initial() {
    return append(SegmentedJournal.ASQN_IGNORE, new InitialEntry().toSerializable(1, serializer));
  }

  Entry configuration() {
    final var member =
        new DefaultRaftMember(MemberId.from("0"), RaftMember.Type.ACTIVE, Instant.EPOCH);
    return append(
        SegmentedJournal.ASQN_IGNORE,
        new ConfigurationEntry(1, List.of(member)).toSerializable(1, serializer));
  }

  /** An application entry of {@code count} records, with random data that doesn't compress. */
  Entry records(int count, int dataLength) {
    final var data = new byte[dataLength];
    random.nextBytes(data);
    final long lowest = nextPosition;
    nextPosition += count;
    final var entry =
        new SerializedApplicationEntry(lowest, nextPosition - 1, ByteBuffer.wrap(data));
    return append(lowest, entry.toSerializable(1, serializer));
  }

  /** The journal's segment files, in order. */
  List<byte[]> files() throws Exception {
    journal.flush();
    try (var files = Files.list(directory)) {
      return files
          .filter(file -> file.toString().endsWith(".log"))
          .sorted()
          .map(
              file -> {
                try {
                  return Files.readAllBytes(file);
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              })
          .toList();
    }
  }

  private Entry append(long asqn, BufferWriter writer) {
    final var record = journal.append(asqn, writer);
    final var bytes = new byte[record.serializedRecord().capacity()];
    record.serializedRecord().getBytes(0, bytes);
    return new Entry(record.checksum(), bytes);
  }

  @Override
  public void close() {
    journal.close();
  }
}
