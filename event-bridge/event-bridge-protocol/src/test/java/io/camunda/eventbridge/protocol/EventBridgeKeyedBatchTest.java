/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.batch.BatchBuilder;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@code KEYED} batch attribute (event-bridge ADR 0001, decision 1): a batch-level
 * declaration that entry keys are meaningful for latest-per-key retention. Entry framing itself
 * already always carries an optional key (unaffected by this attribute) — these tests pin that the
 * flag round-trips, that an un-keyed batch stays byte-identical to the pre-existing builder output,
 * that the CRC covers key bytes, and that the tombstone accessor reflects the documented convention
 * (a keyed entry with an empty value).
 */
final class EventBridgeKeyedBatchTest {

  @Test
  void shouldRoundTripKeyedEntriesIncludingTombstones() {
    // given — a KEYED batch with a normal keyed entry and a tombstone (keyed, empty value)
    final byte[] batch =
        new EventBridgeBatchBuilder()
            .keyed()
            .addEntry(new EventBridgeEntryBuilder().key("k1").value("v1".getBytes(UTF_8)).build())
            .addEntry(new EventBridgeEntryBuilder().key("k2").value(new byte[0]).build())
            .build();
    final var buffer = new UnsafeBuffer(batch);

    // when
    final var iterator = new EventBridgeBatchIterator();
    iterator.wrap(buffer, 0, batch.length);

    // then — the batch declares itself KEYED
    assertThat(iterator.isKeyedBatch()).isTrue();

    final var first = iterator.next();
    assertThat(first.hasKey()).isTrue();
    assertThat(new String(first.getKeyCopy(), UTF_8)).isEqualTo("k1");
    assertThat(new String(first.getValueCopy(), UTF_8)).isEqualTo("v1");
    assertThat(first.isTombstone()).isFalse();

    final var second = iterator.next();
    assertThat(second.hasKey()).isTrue();
    assertThat(new String(second.getKeyCopy(), UTF_8)).isEqualTo("k2");
    assertThat(second.getValueLength()).isZero();
    assertThat(second.isTombstone()).isTrue();

    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  void shouldReadMixedKeyedAndUnkeyedBatchesFromOneBlock() {
    // given — a KEYED batch followed by a plain (unset attribute) batch in the same block
    final byte[] keyedBatch =
        new EventBridgeBatchBuilder()
            .keyed()
            .addEntry(new EventBridgeEntryBuilder().key("k1").value("v1".getBytes(UTF_8)).build())
            .build();
    final byte[] plainBatch =
        new EventBridgeBatchBuilder()
            .addEntry(new EventBridgeEntryBuilder().value("v2".getBytes(UTF_8)).build())
            .build();
    final byte[] block = new byte[keyedBatch.length + plainBatch.length];
    System.arraycopy(keyedBatch, 0, block, 0, keyedBatch.length);
    System.arraycopy(plainBatch, 0, block, keyedBatch.length, plainBatch.length);
    final var buffer = new UnsafeBuffer(block);

    // when — walking batch boundaries with the block iterator
    final var blockIterator = new EventBridgeBatchBlockIterator();
    blockIterator.wrap(buffer, 0, block.length);

    // then — the first batch is KEYED with its keyed entry intact
    assertThat(blockIterator.hasNext()).isTrue();
    blockIterator.next();
    final var firstBatchIterator = new EventBridgeBatchIterator();
    firstBatchIterator.wrap(buffer, 0, blockIterator.currentLength());
    assertThat(firstBatchIterator.isKeyedBatch()).isTrue();
    final var keyedEntry = firstBatchIterator.next();
    assertThat(new String(keyedEntry.getKeyCopy(), UTF_8)).isEqualTo("k1");

    // and — the second batch is not KEYED, its keyless entry intact
    assertThat(blockIterator.hasNext()).isTrue();
    final int secondBatchOffset = keyedBatch.length;
    blockIterator.next();
    final var secondBatchIterator = new EventBridgeBatchIterator();
    secondBatchIterator.wrap(buffer, secondBatchOffset, blockIterator.currentLength());
    assertThat(secondBatchIterator.isKeyedBatch()).isFalse();
    final var plainEntry = secondBatchIterator.next();
    assertThat(plainEntry.hasKey()).isFalse();
    assertThat(new String(plainEntry.getValueCopy(), UTF_8)).isEqualTo("v2");

    assertThat(blockIterator.hasNext()).isFalse();
  }

  @Test
  void shouldLeaveUnkeyedBatchByteIdenticalToTheExistingBuilderOutput() {
    // given — the same entries built with and without ever touching keyed()
    final byte[] withoutKeyedCall =
        new EventBridgeBatchBuilder()
            .addEntry(new EventBridgeEntryBuilder().key("k1").value("v1".getBytes(UTF_8)).build())
            .addEntry(new EventBridgeEntryBuilder().value("v2".getBytes(UTF_8)).build())
            .build();

    // then — the attributes field is unset (0), exactly like every batch built before the KEYED
    // attribute existed, and the CRC over that unchanged framing validates
    final var buffer = new UnsafeBuffer(withoutKeyedCall);
    assertThat(EventBridgeBatch.getAttributes(buffer, 0)).isZero();
    assertThat(EventBridgeBatch.isKeyed(EventBridgeBatch.getAttributes(buffer, 0))).isFalse();
    assertThat(EventBridgeBatch.validateCrc(buffer, 0)).isTrue();

    // and — the pure (client) builder produces the identical byte sequence for the same entries
    final byte[] pureBuilderOutput =
        new BatchBuilder().add("k1", "v1".getBytes(UTF_8)).add("v2".getBytes(UTF_8)).build();
    assertThat(withoutKeyedCall).isEqualTo(pureBuilderOutput);
  }

  @Test
  void shouldSetOnlyTheKeyedAttributeBitWhenMarkingABatchKeyed() {
    // given — the identical entries, built once plain and once with keyed()
    final byte[] plain =
        new EventBridgeBatchBuilder()
            .addEntry(new EventBridgeEntryBuilder().key("k1").value("v1".getBytes(UTF_8)).build())
            .build();
    final byte[] keyed =
        new EventBridgeBatchBuilder()
            .keyed()
            .addEntry(new EventBridgeEntryBuilder().key("k1").value("v1".getBytes(UTF_8)).build())
            .build();

    // then — same total length and same entry bytes; only the attributes field (and the CRC that
    // covers it) differ
    assertThat(keyed.length).isEqualTo(plain.length);
    final var plainBuffer = new UnsafeBuffer(plain);
    final var keyedBuffer = new UnsafeBuffer(keyed);
    assertThat(EventBridgeBatch.getAttributes(plainBuffer, 0)).isZero();
    assertThat(EventBridgeBatch.getAttributes(keyedBuffer, 0))
        .isEqualTo(EventBridgeBatch.KEYED_MASK);

    final int entriesOffset = EventBridgeBatch.entriesOffset(0);
    final byte[] plainEntries = new byte[plain.length - entriesOffset];
    final byte[] keyedEntries = new byte[keyed.length - entriesOffset];
    plainBuffer.getBytes(entriesOffset, plainEntries);
    keyedBuffer.getBytes(entriesOffset, keyedEntries);
    assertThat(keyedEntries).isEqualTo(plainEntries);
  }

  @Test
  void shouldBreakCrcWhenAKeyByteIsCorrupted() {
    // given — a valid KEYED batch
    final byte[] batch =
        new EventBridgeBatchBuilder()
            .keyed()
            .addEntry(new EventBridgeEntryBuilder().key("k1").value("v1".getBytes(UTF_8)).build())
            .build();
    final var buffer = new UnsafeBuffer(batch);
    assertThat(EventBridgeBatch.validateCrc(buffer, 0)).isTrue();

    // when — a single byte inside the key ("k1") is corrupted
    final int keyOffset = EventBridgeBatch.entriesOffset(0) + EventBridgeEntry.ENTRY_HEADER_SIZE;
    buffer.putByte(keyOffset, (byte) (buffer.getByte(keyOffset) ^ 0xFF));

    // then — the CRC no longer validates, proving the key bytes are inside the CRC range
    assertThat(EventBridgeBatch.validateCrc(buffer, 0)).isFalse();
  }

  @Test
  void shouldTreatAnEmptyValueOnAnUnkeyedEntryAsJustEmptyNotATombstone() {
    // given — a keyless entry with an empty value (no key at all)
    final byte[] batch =
        new EventBridgeBatchBuilder()
            .addEntry(new EventBridgeEntryBuilder().value(new byte[0]).build())
            .build();
    final var iterator = new EventBridgeBatchIterator();
    iterator.wrap(new UnsafeBuffer(batch), 0, batch.length);

    // when
    final var entry = iterator.next();

    // then — empty value without a key is just an empty value, not a tombstone
    assertThat(entry.hasKey()).isFalse();
    assertThat(entry.getValueLength()).isZero();
    assertThat(entry.isTombstone()).isFalse();
  }
}
