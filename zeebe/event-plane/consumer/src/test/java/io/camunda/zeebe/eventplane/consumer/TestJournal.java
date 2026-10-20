/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.logstreams.impl.log.SequencedBatch;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A partition's journal, written as Raft and the log stream write it: application entries of real
 * log batches with real records, so tests get the bytes a publisher uploads.
 */
final class TestJournal implements AutoCloseable {

  private final SegmentedJournal journal;
  private final RaftEntrySBESerializer serializer = new RaftEntrySBESerializer();
  private final String typePrefix;
  private long nextPosition = 1;
  private long timestamp = 1_000_000;

  TestJournal(final Path directory) {
    this(directory, "job-");
  }

  /** A journal whose job records are of type {@code {typePrefix}{position}}. */
  TestJournal(final Path directory, final String typePrefix) {
    this.typePrefix = typePrefix;
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

  /** An entry of {@code count} job records, each of type {@code {typePrefix}{position}}. */
  Entry jobs(final int count) {
    final var records = new ArrayList<LogAppendEntry>();
    for (int i = 0; i < count; i++) {
      final long position = nextPosition + i;
      records.add(
          LogAppendEntry.of(
              position * 10,
              new RecordMetadata()
                  .recordType(RecordType.EVENT)
                  .valueType(ValueType.JOB)
                  .intent(JobIntent.CREATED),
              new JobRecord().setType(typePrefix + position).setRetries(3)));
    }
    return records(records);
  }

  /** {@code count} entries of {@code records} job records each. */
  List<Entry> jobEntries(final int count, final int records) {
    final var entries = new ArrayList<Entry>();
    for (int i = 0; i < count; i++) {
      entries.add(jobs(records));
    }
    return entries;
  }

  /** An entry of a log batch of these records, at the next positions. */
  Entry records(final List<LogAppendEntry> records) {
    final long lowest = nextPosition;
    nextPosition += records.size();
    timestamp++;
    final var batch = new SequencedBatch(timestamp, lowest, -1, records);
    final var data = new UnsafeBuffer(new byte[batch.getLength()]);
    batch.write(data, 0);
    final var entry =
        new SerializedApplicationEntry(lowest, nextPosition - 1, ByteBuffer.wrap(data.byteArray()));
    return append(lowest, entry.toSerializable(1, serializer));
  }

  /** An application entry of {@code count} positions whose data isn't a log batch. */
  Entry garbage(final int count) {
    final long lowest = nextPosition;
    nextPosition += count;
    final var entry =
        new SerializedApplicationEntry(lowest, nextPosition - 1, ByteBuffer.wrap(new byte[64]));
    return append(lowest, entry.toSerializable(1, serializer));
  }

  /** Appends another journal's entry unchanged, as a history does that takes that one over. */
  Entry copy(final Entry entry) {
    if (entry.hasRecords()) {
      nextPosition = entry.lastPosition() + 1;
    }
    return entry(journal.append(entry.checksum(), entry.serializedRecord()));
  }

  long lastPosition() {
    return nextPosition - 1;
  }

  private Entry append(final long asqn, final BufferWriter writer) {
    return entry(journal.append(asqn, writer));
  }

  private static Entry entry(final JournalRecord record) {
    final var bytes = new byte[record.serializedRecord().capacity()];
    record.serializedRecord().getBytes(0, bytes);
    return new Entry(record.checksum(), bytes);
  }

  @Override
  public void close() {
    journal.close();
  }
}
