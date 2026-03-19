/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EventDataBatchTest {

  // ---------------------------------------------------------------------------
  // create() — parameter validation
  // ---------------------------------------------------------------------------

  @Nested
  class CreateValidation {

    @Test
    void shouldRejectZeroMaxBatchBytes() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.create(0, 10, 10))
          .withMessageContaining("maxBatchBytes");
    }

    @Test
    void shouldRejectNegativeMaxBatchBytes() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.create(-1, 10, 10))
          .withMessageContaining("maxBatchBytes");
    }

    @Test
    void shouldRejectNegativeMaxEventBytes() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.create(100, -1, 10))
          .withMessageContaining("maxEventBytes");
    }

    @Test
    void shouldAcceptZeroMaxEventBytes() {
      // 0 is valid (only zero-length events pass)
      assertThatNoException().isThrownBy(() -> EventDataBatch.create(100, 0, 10));
    }

    @Test
    void shouldRejectZeroMaxBatchSize() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.create(100, 10, 0))
          .withMessageContaining("maxBatchSize");
    }

    @Test
    void shouldRejectNegativeMaxBatchSize() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.create(100, 10, -1))
          .withMessageContaining("maxBatchSize");
    }
  }

  // ---------------------------------------------------------------------------
  // tryAdd() — happy path and capacity limits
  // ---------------------------------------------------------------------------

  @Nested
  class TryAdd {

    @Test
    void shouldRejectNullEvent() {
      // given
      final var batch = EventDataBatch.create(1000, 100, 10);

      // when / then
      assertThatNullPointerException()
          .isThrownBy(() -> batch.tryAdd(null))
          .withMessage("event must not be null");
    }

    @Test
    void shouldAcceptEventAtExactMaxEventBytesLimit() {
      // given
      final var batch = EventDataBatch.create(1000, 5, 10);
      final var event = new EventData(new byte[5]);

      // when / then
      assertThat(batch.tryAdd(event)).isTrue();
      assertThat(batch.getCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectEventExceedingMaxEventBytes() {
      // given
      final var batch = EventDataBatch.create(1000, 5, 10);
      final var event = new EventData(new byte[6]);

      // when / then
      assertThat(batch.tryAdd(event)).isFalse();
      assertThat(batch.getCount()).isZero();
    }

    @Test
    void shouldAcceptEventBringingTotalToExactMaxBatchBytes() {
      // given
      final var batch = EventDataBatch.create(10, 10, 10);
      final var event = new EventData(new byte[10]);

      // when / then
      assertThat(batch.tryAdd(event)).isTrue();
      assertThat(batch.getSizeInBytes()).isEqualTo(10);
    }

    @Test
    void shouldRejectEventThatWouldExceedMaxBatchBytes() {
      // given
      final var batch = EventDataBatch.create(10, 100, 10);
      batch.tryAdd(new EventData(new byte[8]));

      // when — adding 3 more bytes would bring total to 11 > 10
      final boolean result = batch.tryAdd(new EventData(new byte[3]));

      // then
      assertThat(result).isFalse();
      assertThat(batch.getSizeInBytes()).isEqualTo(8);
    }

    @Test
    void shouldAcceptEventBringingCountToExactMaxBatchSize() {
      // given
      final var batch = EventDataBatch.create(1000, 100, 3);

      // when
      batch.tryAdd(new EventData(new byte[1]));
      batch.tryAdd(new EventData(new byte[1]));
      final boolean result = batch.tryAdd(new EventData(new byte[1]));

      // then
      assertThat(result).isTrue();
      assertThat(batch.getCount()).isEqualTo(3);
    }

    @Test
    void shouldRejectEventThatWouldExceedMaxBatchSize() {
      // given
      final var batch = EventDataBatch.create(1000, 100, 2);
      batch.tryAdd(new EventData(new byte[1]));
      batch.tryAdd(new EventData(new byte[1]));

      // when — count would become 3 > 2
      final boolean result = batch.tryAdd(new EventData(new byte[1]));

      // then
      assertThat(result).isFalse();
      assertThat(batch.getCount()).isEqualTo(2);
    }

    @Test
    void shouldAcceptZeroLengthEvent() {
      // given — maxEventBytes = 0 means only zero-length events are accepted
      final var batch = EventDataBatch.create(100, 0, 10);
      final var empty = new EventData(new byte[0]);

      // when / then
      assertThat(batch.tryAdd(empty)).isTrue();
      assertThat(batch.getSizeInBytes()).isZero();
      assertThat(batch.getCount()).isEqualTo(1);
    }

    @Test
    void shouldThrowOnTryAddInWrapMode() {
      // given
      final var batch = EventDataBatch.create(100, 100, 10);
      batch.tryAdd(new EventData(new byte[] {1, 2, 3}));
      final var wrapBatch = EventDataBatch.fromBytes(batch.toBytes());

      // when / then
      assertThatIllegalStateException()
          .isThrownBy(() -> wrapBatch.tryAdd(new EventData(new byte[0])));
    }

    @Test
    void shouldTrackCountAndSizeAfterMultipleAdds() {
      // given
      final var batch = EventDataBatch.create(1000, 100, 10);

      // when
      batch.tryAdd(new EventData(new byte[] {10, 20, 30}));
      batch.tryAdd(new EventData(new byte[] {1}));
      batch.tryAdd(new EventData(new byte[] {5, 6}));

      // then
      assertThat(batch.getCount()).isEqualTo(3);
      assertThat(batch.getSizeInBytes()).isEqualTo(6); // 3 + 1 + 2
    }
  }

  // ---------------------------------------------------------------------------
  // toBytes() — wire format correctness
  // ---------------------------------------------------------------------------

  @Nested
  class ToBytes {

    @Test
    void shouldSerialiseEmptyBatch() {
      // given
      final var batch = EventDataBatch.create(1000, 100, 10);

      // when
      final byte[] bytes = batch.toBytes();

      // then
      final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
      assertThat(bytes).hasSize(12); // header only
      assertThat(buf.getInt(0)).isEqualTo(12); // total-size
      assertThat(buf.getInt(4)).isZero(); // payload-size
      assertThat(buf.getInt(8)).isZero(); // count
    }

    @Test
    void shouldSerialiseCorrectWireFormat() {
      // given
      final var batch = EventDataBatch.create(1000, 100, 10);
      batch.tryAdd(new EventData(new byte[] {1, 2, 3}));
      batch.tryAdd(new EventData(new byte[] {4, 5}));

      // when
      final byte[] bytes = batch.toBytes();

      // then
      // total-size = 12 + 4*2 + 5 = 25
      assertThat(bytes).hasSize(25);
      final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
      assertThat(buf.getInt(0)).isEqualTo(25); // total-size
      assertThat(buf.getInt(4)).isEqualTo(5); // payload-size = 3 + 2
      assertThat(buf.getInt(8)).isEqualTo(2); // count

      // First event: 4-byte size prefix + body
      assertThat(buf.getInt(12)).isEqualTo(3);
      assertThat(bytes[16]).isEqualTo((byte) 1);
      assertThat(bytes[17]).isEqualTo((byte) 2);
      assertThat(bytes[18]).isEqualTo((byte) 3);

      // Second event
      assertThat(buf.getInt(19)).isEqualTo(2);
      assertThat(bytes[23]).isEqualTo((byte) 4);
      assertThat(bytes[24]).isEqualTo((byte) 5);
    }

    @Test
    void shouldReturnCopyInWrapMode() {
      // given
      final var batch = EventDataBatch.create(100, 100, 10);
      batch.tryAdd(new EventData(new byte[] {42}));
      final var wrapped = EventDataBatch.fromBytes(batch.toBytes());

      // when
      final byte[] a = wrapped.toBytes();
      final byte[] b = wrapped.toBytes();

      // then — two distinct arrays with same content
      assertThat(a).isNotSameAs(b).isEqualTo(b);
    }

    @Test
    void shouldComputeTotalSizeAsHeaderPlusPrefixesPlusPayload() {
      // given
      final var batch = EventDataBatch.create(1000, 100, 10);
      batch.tryAdd(new EventData(new byte[7]));
      batch.tryAdd(new EventData(new byte[3]));

      // when
      final byte[] bytes = batch.toBytes();

      // then: 12 + 4*2 + 10 = 30
      assertThat(bytes).hasSize(30);
      assertThat(batch.getSizeInBytes()).isEqualTo(10); // payload only
    }
  }

  // ---------------------------------------------------------------------------
  // fromBytes() — header validation
  // ---------------------------------------------------------------------------

  @Nested
  class FromBytesValidation {

    @Test
    void shouldRejectNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> EventDataBatch.fromBytes(null))
          .withMessage("bytes must not be null");
    }

    @Test
    void shouldRejectBufferShorterThanHeader() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.fromBytes(new byte[11]))
          .withMessageContaining("buffer too short");
    }

    @Test
    void shouldRejectTotalSizeLessThan12() {
      // given — craft a buffer where total-size field is 4
      final byte[] bytes = new byte[12];
      ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(0, 4);
      // Note: total-size (4) != bytes.length (12), but the total-size < 12 check fires first

      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.fromBytes(bytes))
          .withMessageContaining("total-size");
    }

    @Test
    void shouldRejectTotalSizeMismatchWithBufferLength() {
      // given — total-size = 20, buffer = 15 bytes
      final byte[] bytes = new byte[15];
      ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(0, 20);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.fromBytes(bytes))
          .withMessageContaining("total-size");
    }

    @Test
    void shouldRejectNegativePayloadSize() {
      // given — header-only buffer with negative payload-size
      final ByteBuffer buf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
      buf.putInt(12); // total-size
      buf.putInt(-1); // payload-size (negative)
      buf.putInt(0); // count

      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.fromBytes(buf.array()))
          .withMessageContaining("payload-size");
    }

    @Test
    void shouldRejectNegativeCount() {
      // given — header-only buffer with negative count
      final ByteBuffer buf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
      buf.putInt(12); // total-size
      buf.putInt(0); // payload-size
      buf.putInt(-1); // count (negative)

      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.fromBytes(buf.array()))
          .withMessageContaining("count");
    }

    @Test
    void shouldMakeDefensiveCopy() {
      // given — a valid serialised batch
      final var original = EventDataBatch.create(100, 100, 10);
      original.tryAdd(new EventData(new byte[] {99}));
      final byte[] bytes = original.toBytes();

      // when
      final var wrapped = EventDataBatch.fromBytes(bytes);

      // mutate the input array after construction
      bytes[0] = (byte) 0xFF;

      // then — the wrapped batch is unaffected
      assertThat(wrapped.getCount()).isEqualTo(1);
      final var iter = wrapped.getEvents().iterator();
      assertThat(iter.next().body()).containsExactly(99);
    }

    @Test
    void shouldReadCountAndSizeFromHeaderWithoutIteration() {
      // given — a batch with 3 events
      final var original = EventDataBatch.create(1000, 100, 10);
      original.tryAdd(new EventData(new byte[] {1, 2, 3}));
      original.tryAdd(new EventData(new byte[] {4}));
      original.tryAdd(new EventData(new byte[] {5, 6}));

      // when
      final var wrapped = EventDataBatch.fromBytes(original.toBytes());

      // then — metadata is available without calling getEvents()
      assertThat(wrapped.getCount()).isEqualTo(3);
      assertThat(wrapped.getSizeInBytes()).isEqualTo(6); // 3 + 1 + 2
    }
  }

  // ---------------------------------------------------------------------------
  // getEvents() — wrap-mode lazy iteration
  // ---------------------------------------------------------------------------

  @Nested
  class GetEventsWrapMode {

    @Test
    void shouldIterateAllEventsInOrder() {
      // given
      final var original = EventDataBatch.create(1000, 100, 10);
      original.tryAdd(new EventData(new byte[] {10, 20}));
      original.tryAdd(new EventData(new byte[] {30}));
      original.tryAdd(new EventData(new byte[] {40, 50, 60}));

      final var wrapped = EventDataBatch.fromBytes(original.toBytes());

      // when
      final List<byte[]> payloads = new ArrayList<>();
      for (final EventData e : wrapped.getEvents()) {
        payloads.add(e.body());
      }

      // then
      assertThat(payloads).hasSize(3);
      assertThat(payloads.get(0)).containsExactly(10, 20);
      assertThat(payloads.get(1)).containsExactly(30);
      assertThat(payloads.get(2)).containsExactly(40, 50, 60);
    }

    @Test
    void shouldBeReIterable() {
      // given
      final var original = EventDataBatch.create(100, 100, 10);
      original.tryAdd(new EventData(new byte[] {7, 8}));
      final var wrapped = EventDataBatch.fromBytes(original.toBytes());

      // when — iterate twice
      final List<byte[]> first = new ArrayList<>();
      final List<byte[]> second = new ArrayList<>();
      for (final EventData e : wrapped.getEvents()) {
        first.add(e.body());
      }
      for (final EventData e : wrapped.getEvents()) {
        second.add(e.body());
      }

      // then — both traversals produce the same content
      assertThat(first).hasSize(1);
      assertThat(second).hasSize(1);
      assertThat(first.get(0)).containsExactly(7, 8);
      assertThat(second.get(0)).containsExactly(7, 8);
    }

    @Test
    void shouldDetectTruncatedFrameOnIteration() {
      // given — craft a batch claiming 1 event with frame size 100, but only provide 5 body bytes
      final ByteBuffer buf = ByteBuffer.allocate(12 + 4 + 5).order(ByteOrder.BIG_ENDIAN);
      buf.putInt(12 + 4 + 5); // total-size (matches buffer length — passes header check)
      buf.putInt(5); // payload-size (5 bytes declared)
      buf.putInt(1); // count = 1
      buf.putInt(100); // frame size = 100 — but only 5 bytes follow
      buf.put(new byte[5]); // only 5 payload bytes

      // when / then — structural error is now detected eagerly at fromBytes()
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.fromBytes(buf.array()))
          .withMessageContaining("Truncated");
    }

    @Test
    void shouldDetectPayloadSizeMismatchAfterFullTraversal() {
      // given — header says payload-size = 99 but frames total 5
      final ByteBuffer buf = ByteBuffer.allocate(12 + 4 + 5).order(ByteOrder.BIG_ENDIAN);
      buf.putInt(12 + 4 + 5); // total-size
      buf.putInt(99); // payload-size (wrong!)
      buf.putInt(1); // count = 1
      buf.putInt(5); // frame size = 5
      buf.put(new byte[5]);

      // when / then — consistency check is now detected eagerly at fromBytes()
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.fromBytes(buf.array()))
          .withMessageContaining("Payload-size mismatch");
    }

    @Test
    void shouldFireConsistencyCheckOnEveryTraversal() {
      // given — same tampered buffer as above
      final ByteBuffer buf = ByteBuffer.allocate(12 + 4 + 3).order(ByteOrder.BIG_ENDIAN);
      buf.putInt(12 + 4 + 3); // total-size
      buf.putInt(50); // payload-size (wrong)
      buf.putInt(1);
      buf.putInt(3);
      buf.put(new byte[3]);

      // when / then — consistency check is now detected eagerly at fromBytes()
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EventDataBatch.fromBytes(buf.array()))
          .withMessageContaining("Payload-size mismatch");
    }
  }

  // ---------------------------------------------------------------------------
  // getEvents() — build-mode
  // ---------------------------------------------------------------------------

  @Nested
  class GetEventsBuildMode {

    @Test
    void shouldIterateEventsInBuildMode() {
      // given
      final var batch = EventDataBatch.create(1000, 100, 10);
      batch.tryAdd(new EventData(new byte[] {1}));
      batch.tryAdd(new EventData(new byte[] {2, 3}));

      // when
      final List<byte[]> payloads = new ArrayList<>();
      for (final EventData e : batch.getEvents()) {
        payloads.add(e.body());
      }

      // then
      assertThat(payloads).hasSize(2);
      assertThat(payloads.get(0)).containsExactly(1);
      assertThat(payloads.get(1)).containsExactly(2, 3);
    }

    @Test
    void shouldBeReIterableInBuildMode() {
      // given
      final var batch = EventDataBatch.create(1000, 100, 10);
      batch.tryAdd(new EventData(new byte[] {99}));

      // when
      final List<byte[]> first = new ArrayList<>();
      final List<byte[]> second = new ArrayList<>();
      for (final EventData e : batch.getEvents()) {
        first.add(e.body());
      }
      for (final EventData e : batch.getEvents()) {
        second.add(e.body());
      }

      // then
      assertThat(first.get(0)).containsExactly(99);
      assertThat(second.get(0)).containsExactly(99);
    }
  }

  // ---------------------------------------------------------------------------
  // Round-trip: build → toBytes() → fromBytes() → getEvents()
  // ---------------------------------------------------------------------------

  @Nested
  class RoundTrip {

    @Test
    void shouldRoundTripSingleEvent() {
      // given
      final byte[] payload = {10, 20, 30, 40, 50};
      final var batch = EventDataBatch.create(1000, 100, 10);
      batch.tryAdd(new EventData(payload));

      // when
      final var wrapped = EventDataBatch.fromBytes(batch.toBytes());

      // then
      assertThat(wrapped.getCount()).isEqualTo(1);
      assertThat(wrapped.getSizeInBytes()).isEqualTo(5);
      final var events = new ArrayList<EventData>();
      for (final EventData e : wrapped.getEvents()) {
        events.add(e);
      }
      assertThat(events).hasSize(1);
      assertThat(events.get(0).body()).containsExactly(10, 20, 30, 40, 50);
    }

    @Test
    void shouldRoundTripMultipleEvents() {
      // given
      final var batch = EventDataBatch.create(1000, 100, 10);
      batch.tryAdd(new EventData(new byte[] {1, 2, 3}));
      batch.tryAdd(new EventData(new byte[] {})); // zero-length
      batch.tryAdd(new EventData(new byte[] {7, 8}));

      // when
      final var wrapped = EventDataBatch.fromBytes(batch.toBytes());

      // then
      assertThat(wrapped.getCount()).isEqualTo(3);
      assertThat(wrapped.getSizeInBytes()).isEqualTo(5); // 3 + 0 + 2

      final List<byte[]> payloads = new ArrayList<>();
      for (final EventData e : wrapped.getEvents()) {
        payloads.add(e.body());
      }
      assertThat(payloads.get(0)).containsExactly(1, 2, 3);
      assertThat(payloads.get(1)).isEmpty();
      assertThat(payloads.get(2)).containsExactly(7, 8);
    }

    @Test
    void shouldRoundTripEmptyBatch() {
      // given
      final var batch = EventDataBatch.create(1000, 100, 10);

      // when
      final var wrapped = EventDataBatch.fromBytes(batch.toBytes());

      // then
      assertThat(wrapped.getCount()).isZero();
      assertThat(wrapped.getSizeInBytes()).isZero();
      assertThat(wrapped.getEvents().iterator().hasNext()).isFalse();
    }

    @Test
    void shouldPreserveBigEndianEncoding() {
      // given — craft a batch with a known payload and verify bytes directly
      final var batch = EventDataBatch.create(1000, 100, 10);
      batch.tryAdd(new EventData(new byte[] {(byte) 0xAB, (byte) 0xCD}));

      // when
      final byte[] bytes = batch.toBytes();

      // then — total-size at offset 0, big-endian: 12 + 4 + 2 = 18 = 0x00000012
      assertThat(bytes[0]).isZero();
      assertThat(bytes[1]).isZero();
      assertThat(bytes[2]).isZero();
      assertThat(bytes[3]).isEqualTo((byte) 18);

      // payload-size at offset 4: 2 = 0x00000002
      assertThat(bytes[7]).isEqualTo((byte) 2);

      // count at offset 8: 1 = 0x00000001
      assertThat(bytes[11]).isEqualTo((byte) 1);

      // frame size at offset 12: 2 = 0x00000002
      assertThat(bytes[15]).isEqualTo((byte) 2);

      // payload
      assertThat(bytes[16]).isEqualTo((byte) 0xAB);
      assertThat(bytes[17]).isEqualTo((byte) 0xCD);
    }
  }
}
