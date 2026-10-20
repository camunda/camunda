/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class RaftEntryInfoTest {

  private final RaftEntrySBESerializer raft = new RaftEntrySBESerializer();

  @Test
  void shouldReadTermAndPositionsOfApplicationEntry() {
    // given
    final var entry =
        new SerializedApplicationEntry(
            41, 45, new UnsafeBuffer(ByteBuffer.wrap(new byte[] {1, 2})));
    final var buffer = new UnsafeBuffer(new byte[raft.getApplicationEntrySerializedLength(entry)]);
    raft.writeApplicationEntry(7, entry, buffer, 0);

    // when
    final var info = RaftEntryInfo.read(journalRecord(buffer.byteArray(), 41));

    // then
    assertThat(info).isEqualTo(new RaftEntryInfo(7, 41, 45));
  }

  @Test
  void shouldReadTermOfInitialEntryWithoutPositions() {
    // given
    final var buffer = new UnsafeBuffer(new byte[raft.getInitialEntrySerializedLength()]);
    raft.writeInitialEntry(9, new InitialEntry(), buffer, 0);

    // when
    final var info = RaftEntryInfo.read(journalRecord(buffer.byteArray(), -1));

    // then
    assertThat(info).isEqualTo(new RaftEntryInfo(9, -1, -1));
  }

  @Test
  void shouldRejectBytesThatAreNoJournalRecord() {
    assertThatThrownBy(() -> RaftEntryInfo.read(new byte[] {1, 2, 3}))
        .isInstanceOf(LogObjectFormatException.class);
  }

  private static byte[] journalRecord(final byte[] raftEntry, final long asqn) {
    final byte[] serialized = new byte[8 + 8 + 8 + 4 + raftEntry.length];
    new SBESerializer()
        .writeData(
            1,
            asqn,
            new DirectBufferWriter(new UnsafeBuffer(raftEntry)),
            new UnsafeBuffer(serialized),
            0)
        .get();
    return serialized;
  }
}
