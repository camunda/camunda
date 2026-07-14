/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.logstreams;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.eventbridge.protocol.EventBridgeBatchBlockIterator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Pins the cursor's header-peek fast path against the full raft-entry deserialization it replaced:
 * for every batch, the peeked bounds must be identical to those derived from {@code
 * readRaftLogEntry} and the application entry's data slice. The cursor feeds both the fetch index
 * scan and the append path on leader and follower, so a divergence corrupts served batches.
 */
final class ApplicationEntryCursorAdapterTest {

  private final RaftEntrySBESerializer serializer = new RaftEntrySBESerializer();
  private final ApplicationEntryCursorAdapter cursor = new ApplicationEntryCursorAdapter();

  @Test
  void shouldExposeSameBatchBoundsAsFullDeserialization() {
    // given - an application entry holding three batches, wrapped at a nonzero file offset
    final byte[] block = concat(batch(1, 3, 24), batch(4, 4, 8), batch(5, 10, 0));
    final DirectBuffer entryBuffer = writeApplicationEntry(1, 10, block);
    final int fileOffset = 100;

    // when
    cursor.wrap(entryBuffer, fileOffset);

    // then - every batch matches the reference derived via readRaftLogEntry
    final List<ReferenceBatch> expected = referenceBatches(entryBuffer, fileOffset);
    assertThat(expected).hasSize(3);
    for (final ReferenceBatch reference : expected) {
      assertThat(cursor.hasNext()).isTrue();
      cursor.next();
      assertThat(cursor.currentOffset()).isEqualTo(reference.offset());
      assertThat(cursor.currentLowestAsqn()).isEqualTo(reference.lowestAsqn());
      assertThat(cursor.currentHighestAsqn()).isEqualTo(reference.highestAsqn());
      assertThat(cursor.currentLength()).isEqualTo(reference.length());
      assertThat(
              sliceBytes(entryBuffer, cursor.currentOffset() - fileOffset, cursor.currentLength()))
          .isEqualTo(reference.bytes());
    }
    assertThat(cursor.hasNext()).isFalse();
  }

  @Test
  void shouldNotIterateInitialEntry() {
    // given
    final var buffer = new ExpandableArrayBuffer();
    final int length = serializer.writeInitialEntry(1, new InitialEntry(), buffer, 0);

    // when
    cursor.wrap(new UnsafeBuffer(buffer, 0, length), 0);

    // then
    assertThat(cursor.hasNext()).isFalse();
  }

  @Test
  void shouldNotIterateConfigurationEntry() {
    // given
    final Set<RaftMember> members =
        Set.of(new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.ofEpochMilli(123L)));
    final var buffer = new ExpandableArrayBuffer();
    final int length =
        serializer.writeConfigurationEntry(1, new ConfigurationEntry(123L, members), buffer, 0);

    // when
    cursor.wrap(new UnsafeBuffer(buffer, 0, length), 0);

    // then
    assertThat(cursor.hasNext()).isFalse();
  }

  @Test
  void shouldNotIterateZeroLengthApplicationData() {
    // given
    final DirectBuffer entryBuffer = writeApplicationEntry(1, 1, new byte[0]);

    // when
    cursor.wrap(entryBuffer, 0);

    // then
    assertThat(cursor.hasNext()).isFalse();
  }

  // ---------------------------------------------------------------------------------------------
  // fixtures
  // ---------------------------------------------------------------------------------------------

  /**
   * Derives the expected per-batch bounds the way the cursor used to compute them: full raft-entry
   * deserialization, iterating the application entry's data slice, with the batch offsets rebased
   * onto the serialized header length.
   */
  private List<ReferenceBatch> referenceBatches(
      final DirectBuffer entryBuffer, final int fileOffset) {
    final var raftEntry = serializer.readRaftLogEntry(entryBuffer);
    assertThat(raftEntry.isApplicationEntry()).isTrue();
    final var applicationEntry = (SerializedApplicationEntry) raftEntry.getApplicationEntry();
    final DirectBuffer data = applicationEntry.data();
    final int baseOffset = fileOffset + serializer.getApplicationEntrySerializedHeaderLength();

    final var iterator = new EventBridgeBatchBlockIterator();
    iterator.wrap(data, 0, data.capacity());

    final var references = new ArrayList<ReferenceBatch>();
    int blockOffset = 0;
    while (iterator.hasNext()) {
      iterator.next();
      references.add(
          new ReferenceBatch(
              baseOffset + blockOffset,
              iterator.currentLowestPosition(),
              iterator.currentHighestPosition(),
              iterator.currentLength(),
              sliceBytes(data, blockOffset, iterator.currentLength())));
      blockOffset += iterator.currentLength();
    }
    return references;
  }

  private DirectBuffer writeApplicationEntry(
      final long lowestPosition, final long highestPosition, final byte[] data) {
    final var entry =
        new SerializedApplicationEntry(lowestPosition, highestPosition, new UnsafeBuffer(data));
    final var buffer = new ExpandableArrayBuffer();
    final int length = serializer.writeApplicationEntry(1, entry, buffer, 0);
    return new UnsafeBuffer(buffer, 0, length);
  }

  private static byte[] batch(
      final long lowestPosition, final long highestPosition, final int payloadLength) {
    final var bytes = new byte[EventBridgeBatch.HEADER_LENGTH + payloadLength];
    final var buffer = new UnsafeBuffer(bytes);
    EventBridgeBatch.writeHeader(
        buffer, 0, payloadLength, 0, (int) (highestPosition - lowestPosition + 1));
    EventBridgeBatch.patchPosition(buffer, 0, lowestPosition);
    for (int i = 0; i < payloadLength; i++) {
      // derive the payload from the range so every batch has distinguishable content
      bytes[EventBridgeBatch.HEADER_LENGTH + i] = (byte) (31 * lowestPosition + i);
    }
    return bytes;
  }

  private static byte[] concat(final byte[]... batches) {
    int length = 0;
    for (final byte[] batch : batches) {
      length += batch.length;
    }
    final var block = new byte[length];
    int offset = 0;
    for (final byte[] batch : batches) {
      System.arraycopy(batch, 0, block, offset, batch.length);
      offset += batch.length;
    }
    return block;
  }

  private static byte[] sliceBytes(final DirectBuffer buffer, final int offset, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(offset, bytes);
    return bytes;
  }

  private record ReferenceBatch(
      int offset, long lowestAsqn, long highestAsqn, int length, byte[] bytes) {}
}
