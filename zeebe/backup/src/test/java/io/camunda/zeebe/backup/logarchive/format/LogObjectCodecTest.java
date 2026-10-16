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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LogObjectCodecTest {

  /** Every third frame (index % 3 == 0) is a non-application frame; others hold 2 positions. */
  private static List<LogArchiveFrame> frames(final long firstIndex, final int count) {
    final List<LogArchiveFrame> result = new ArrayList<>();
    long position = 1000;
    for (int i = 0; i < count; i++) {
      final long index = firstIndex + i;
      final byte[] payload = new byte[10 + (i % 7)];
      Arrays.fill(payload, (byte) i);
      if (index % 3 == 0) {
        result.add(new LogArchiveFrame(index, 1, -1, -1, -1, index * 7, payload));
      } else {
        result.add(
            new LogArchiveFrame(index, 1, position, position, position + 1, index * 7, payload));
        position += 2;
      }
    }
    return result;
  }

  @Test
  void shouldRoundTripMixedFrames() {
    // given
    final var frames = frames(10, 20);

    // when
    final var encoded = LogObjectEncoder.encode("tl-0", 3, 5, frames);
    final var decoded = LogObjectDecoder.decode(encoded.bytes());

    // then
    assertThat(decoded.frames()).isEqualTo(frames);
    assertThat(decoded.header()).isEqualTo(encoded.header());
    assertThat(decoded.header().timelineId()).isEqualTo("tl-0");
    assertThat(decoded.header().partitionId()).isEqualTo(3);
    assertThat(decoded.header().term()).isEqualTo(5);
    assertThat(decoded.header().entryCount()).isEqualTo(20);
  }

  @Test
  void shouldComputeFirstAndLastPositionFromApplicationFrames() {
    // given index 9 is non-application (first), index 12 is non-application (last)
    final var frames = frames(9, 4);

    // when
    final var header = LogObjectEncoder.encode("tl-0", 1, 1, frames).header();

    // then
    assertThat(header.firstIndex()).isEqualTo(9);
    assertThat(header.lastIndex()).isEqualTo(12);
    assertThat(header.firstPosition()).isEqualTo(1000);
    assertThat(header.lastPosition()).isEqualTo(1003);
  }

  @Test
  void shouldUseMinusOnePositionsWithoutApplicationFrames() {
    // given
    final var frame = new LogArchiveFrame(3, 1, -1, -1, -1, 1, new byte[] {1});

    // when
    final var encoded = LogObjectEncoder.encode("tl-0", 1, 1, List.of(frame));

    // then
    assertThat(encoded.header().firstPosition()).isEqualTo(-1);
    assertThat(encoded.header().lastPosition()).isEqualTo(-1);
    assertThat(LogObjectDecoder.decode(encoded.bytes()).frames()).containsExactly(frame);
  }

  @Test
  void shouldRoundTripSingleFrameObject() {
    // given
    final var frame = new LogArchiveFrame(1, 1, 5, 5, 8, 42, new byte[0]);

    // when
    final var decoded =
        LogObjectDecoder.decode(LogObjectEncoder.encode("t", 1, 2, List.of(frame)).bytes());

    // then
    assertThat(decoded.frames()).containsExactly(frame);
    assertThat(decoded.footer().entries()).hasSize(1);
  }

  @Test
  void shouldWriteFooterEntryForFirstAndEvery64thFrame() {
    // given
    final var frames = frames(100, 200);

    // when
    final var footer =
        LogObjectDecoder.readFooter(LogObjectEncoder.encode("tl-0", 1, 1, frames).bytes());

    // then
    assertThat(footer.entries())
        .extracting(LogObjectFooter.Entry::index)
        .containsExactly(100L, 164L, 228L, 292L);
  }

  @Test
  void shouldReadFooterFromTailOnly() {
    // given
    final var bytes = LogObjectEncoder.encode("tl-0", 1, 1, frames(1, 200)).bytes();
    final long footerOffset = LogObjectDecoder.readFooterOffset(bytes);
    final long tailStart = footerOffset - 5;

    // when
    final var footer =
        LogObjectDecoder.readFooter(
            Arrays.copyOfRange(bytes, (int) tailStart, bytes.length), tailStart);

    // then
    assertThat(footer).isEqualTo(LogObjectDecoder.readFooter(bytes));
  }

  @Test
  void shouldFindFrameOffsetBeforeInsideAndAfter() {
    // given 200 frames from index 1; frame offsets of sparse entries are in the footer
    final var frames = frames(1, 200);
    final var bytes = LogObjectEncoder.encode("tl-0", 1, 1, frames).bytes();
    final var footer = LogObjectDecoder.readFooter(bytes);
    final var entries = footer.entries();
    // entry 0 is index 1, entry 1 is index 65 (non-app: 65 % 3 != 0 -> app), pick by data
    final long secondLowest = entries.get(1).lowestPosition();

    // when / then
    assertThat(LogObjectDecoder.findFrameOffset(footer, 0)).isEqualTo(entries.get(0).frameOffset());
    assertThat(LogObjectDecoder.findFrameOffset(footer, secondLowest - 1))
        .isEqualTo(entries.get(0).frameOffset());
    assertThat(LogObjectDecoder.findFrameOffset(footer, secondLowest))
        .isEqualTo(entries.get(1).frameOffset());
    assertThat(LogObjectDecoder.findFrameOffset(footer, secondLowest + 3))
        .isEqualTo(entries.get(1).frameOffset());
    assertThat(LogObjectDecoder.findFrameOffset(footer, Long.MAX_VALUE))
        .isEqualTo(entries.get(entries.size() - 1).frameOffset());
  }

  @Test
  void shouldSkipFooterEntriesOfNonApplicationFrames() {
    // given index 63 is non-application, and it is the 64th frame -> footer entry with -1
    final var frames = new ArrayList<LogArchiveFrame>();
    for (int i = 0; i < 130; i++) {
      final long index = i;
      frames.add(
          i == 64
              ? new LogArchiveFrame(index, 1, -1, -1, -1, 0, new byte[1])
              : new LogArchiveFrame(index, 1, 100 + i, 100 + i, 100 + i, 0, new byte[1]));
    }
    final var footer =
        LogObjectDecoder.readFooter(LogObjectEncoder.encode("tl-0", 1, 1, frames).bytes());
    assertThat(footer.entries().get(1).lowestPosition()).isEqualTo(-1);

    // when
    final long offset = LogObjectDecoder.findFrameOffset(footer, 100 + 70);

    // then the non-application entry is ignored, scanning starts at the previous one
    assertThat(offset).isEqualTo(footer.entries().get(0).frameOffset());
    assertThat(LogObjectDecoder.findFrameOffset(footer, 100 + 129))
        .isEqualTo(footer.entries().get(2).frameOffset());
  }

  @Test
  void shouldComputeByteRangeForPositionRange() {
    // given
    final var bytes = LogObjectEncoder.encode("tl-0", 1, 1, frames(1, 300)).bytes();
    final var footer = LogObjectDecoder.readFooter(bytes);
    final var entries = footer.entries();

    // when
    final var all = LogObjectDecoder.byteRange(footer, 0);
    final var first = LogObjectDecoder.byteRange(footer, 0, 1001);
    final var middle =
        LogObjectDecoder.byteRange(
            footer, entries.get(1).lowestPosition(), entries.get(1).lowestPosition());

    // then
    assertThat(all.start()).isEqualTo(entries.get(0).frameOffset());
    assertThat(all.endExclusive()).isEqualTo(footer.footerOffset());
    assertThat(first.endExclusive()).isEqualTo(entries.get(1).frameOffset());
    assertThat(middle.start()).isEqualTo(entries.get(1).frameOffset());
    // entry 2 (index 129) is a non-application frame and is skipped as an upper bound
    assertThat(middle.endExclusive()).isEqualTo(entries.get(3).frameOffset());
  }

  @Test
  void shouldProduceStableSha256() {
    // given
    final var frames = frames(1, 50);

    // when
    final var a = LogObjectEncoder.encode("tl-0", 1, 1, frames);
    final var b = LogObjectEncoder.encode("tl-0", 1, 1, frames);
    final var c = LogObjectEncoder.encode("tl-0", 1, 2, frames);

    // then
    assertThat(a.sha256()).hasSize(64).isEqualTo(b.sha256()).isNotEqualTo(c.sha256());
    assertThat(a.bytes()).isEqualTo(b.bytes());
  }

  @Test
  void shouldRecordWriterTermEvenIfFramesHaveLowerTerm() {
    // given frames() use term 1
    // when
    final var encoded = LogObjectEncoder.encode("tl-0", 1, 9, frames(1, 3));

    // then
    assertThat(encoded.header().term()).isEqualTo(9);
    assertThat(LogObjectDecoder.decode(encoded.bytes()).frames()).allMatch(f -> f.term() == 1);
  }

  @Test
  void shouldRoundTripLargeObject() {
    // given
    final var frames = frames(1, 10_000);

    // when
    final var encoded = LogObjectEncoder.encode("tl-0", 1, 1, frames);
    final var decoded = LogObjectDecoder.decode(encoded.bytes());

    // then
    assertThat(decoded.frames()).isEqualTo(frames);
    assertThat(decoded.footer().entries()).hasSize((10_000 + 63) / 64);
  }

  @Test
  void shouldRejectInvalidInput() {
    final var f1 = new LogArchiveFrame(1, 1, 1, 1, 1, 0, new byte[0]);
    final var gap = new LogArchiveFrame(3, 1, 3, 3, 3, 0, new byte[0]);
    final var sameAsqn = new LogArchiveFrame(2, 1, 1, 1, 1, 0, new byte[0]);

    assertThatThrownBy(() -> LogObjectEncoder.encode("t", 1, 1, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LogObjectEncoder.encode("t", 1, 1, List.of(f1, gap)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contiguous");
    assertThatThrownBy(() -> LogObjectEncoder.encode("t", 1, 1, List.of(f1, sameAsqn)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("asqn");
  }

  @Test
  void shouldDetectCorruptHeaderMagic() {
    // given
    final var bytes = LogObjectEncoder.encode("tl-0", 1, 1, frames(1, 5)).bytes();
    bytes[0] ^= 1;

    // when / then
    assertThatThrownBy(() -> LogObjectDecoder.decode(bytes))
        .isInstanceOf(LogObjectFormatException.class)
        .hasMessageContaining("header magic");
  }

  @Test
  void shouldDetectCorruptTrailerMagic() {
    // given
    final var bytes = LogObjectEncoder.encode("tl-0", 1, 1, frames(1, 5)).bytes();
    bytes[bytes.length - 1] ^= 1;

    // when / then
    assertThatThrownBy(() -> LogObjectDecoder.decode(bytes))
        .isInstanceOf(LogObjectFormatException.class)
        .hasMessageContaining("trailer magic");
  }

  @Test
  void shouldDetectTrailerFooterOffsetMismatch() {
    // given
    final var bytes = LogObjectEncoder.encode("tl-0", 1, 1, frames(1, 5)).bytes();
    final int pos = bytes.length - 12;
    ByteBuffer.wrap(bytes).putLong(pos, ByteBuffer.wrap(bytes).getLong(pos) - 1);

    // when / then
    assertThatThrownBy(() -> LogObjectDecoder.decode(bytes))
        .isInstanceOf(LogObjectFormatException.class);
  }

  @Test
  void shouldDetectTruncation() {
    // given
    final var bytes = LogObjectEncoder.encode("tl-0", 1, 1, frames(1, 50)).bytes();

    // when / then
    for (final int length : new int[] {0, 3, 20, bytes.length / 2, bytes.length - 1}) {
      final var truncated = Arrays.copyOf(bytes, length);
      assertThatThrownBy(() -> LogObjectDecoder.decode(truncated))
          .as("length %d", length)
          .isInstanceOf(LogObjectFormatException.class);
    }
  }

  @Test
  void shouldDetectCorruptFrameLength() {
    // given
    final var encoded = LogObjectEncoder.encode("tl-0", 1, 1, frames(1, 5));
    final var bytes = encoded.bytes();
    final int lengthPos = encoded.header().headerLength() + 48;
    ByteBuffer.wrap(bytes).putInt(lengthPos, Integer.MAX_VALUE);

    // when / then
    assertThatThrownBy(() -> LogObjectDecoder.decode(bytes))
        .isInstanceOf(LogObjectFormatException.class);
  }
}
