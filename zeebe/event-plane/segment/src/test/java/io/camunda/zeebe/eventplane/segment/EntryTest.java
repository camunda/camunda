/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import static io.camunda.zeebe.eventplane.segment.Entry.NO_POSITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.storage.serializer.ApplicationEntryDecoder;
import io.atomix.raft.storage.serializer.EntryType;
import io.atomix.raft.storage.serializer.RaftLogEntryDecoder;
import io.camunda.zeebe.journal.file.RecordDataDecoder;
import java.nio.file.Path;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks that {@link Entry}'s own parsing reads what C8's generated SBE decoders read. */
final class EntryTest {

  @TempDir Path directory;

  @Test
  void readsWhatC8sDecodersRead() {
    try (var journal = new RaftJournal(directory)) {
      final var entries =
          List.of(
              journal.initial(),
              journal.records(3, 200),
              journal.configuration(),
              journal.records(1, 10));

      for (var entry : entries) {
        final var c8 = C8Entry.decode(entry.serializedRecord());
        assertThat(entry.index()).isEqualTo(c8.index());
        assertThat(entry.firstPosition()).isEqualTo(c8.asqn());
        assertThat(entry.hasRecords()).isEqualTo(c8.type() == EntryType.ApplicationEntry);
        assertThat(entry.lastPosition()).isEqualTo(c8.highestPosition());
        final var raftEntry = new byte[entry.raftEntry().remaining()];
        entry.raftEntry().get(raftEntry);
        assertThat(raftEntry).isEqualTo(c8.raftEntry());
        if (entry.hasRecords()) {
          final var data = entry.applicationData();
          assertThat(data.isReadOnly()).isTrue();
          final var applicationData = new byte[data.remaining()];
          data.get(applicationData);
          assertThat(applicationData).isEqualTo(c8.applicationData());
        }
      }
    }
  }

  @Test
  void shouldRefuseApplicationDataOfAnEntryWithoutRecords() {
    try (var journal = new RaftJournal(directory)) {
      // given
      final var entry = journal.initial();

      // when / then
      assertThatThrownBy(entry::applicationData)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Expected an entry with records, but entry 1 holds none");
    }
  }

  /** An entry's fields, as C8's decoders read them. */
  private record C8Entry(
      long index,
      long asqn,
      EntryType type,
      long highestPosition,
      byte[] raftEntry,
      byte[] applicationData) {

    static C8Entry decode(byte[] serializedRecord) {
      final var record = new UnsafeBuffer(serializedRecord);
      final var recordHeader =
          new io.camunda.zeebe.journal.file.MessageHeaderDecoder().wrap(record, 0);
      final var data =
          new RecordDataDecoder()
              .wrap(
                  record,
                  recordHeader.encodedLength(),
                  recordHeader.blockLength(),
                  recordHeader.version());
      final long index = data.index();
      final long asqn = data.asqn();
      final var raft = new UnsafeBuffer();
      data.wrapData(raft);
      final var raftEntry = new byte[raft.capacity()];
      raft.getBytes(0, raftEntry);

      final var raftHeader =
          new io.atomix.raft.storage.serializer.MessageHeaderDecoder().wrap(raft, 0);
      final var logEntry =
          new RaftLogEntryDecoder()
              .wrap(
                  raft, raftHeader.encodedLength(), raftHeader.blockLength(), raftHeader.version());
      final var type = logEntry.type();
      long highestPosition = NO_POSITION;
      var applicationData = new byte[0];
      if (type == EntryType.ApplicationEntry) {
        final int offset = raftHeader.encodedLength() + logEntry.encodedLength();
        raftHeader.wrap(raft, offset);
        final var application =
            new ApplicationEntryDecoder()
                .wrap(
                    raft,
                    offset + raftHeader.encodedLength(),
                    raftHeader.blockLength(),
                    raftHeader.version());
        highestPosition = application.highestAsqn();
        applicationData = new byte[application.applicationDataLength()];
        application.getApplicationData(applicationData, 0, applicationData.length);
      }
      return new C8Entry(index, asqn, type, highestPosition, raftEntry, applicationData);
    }
  }
}
