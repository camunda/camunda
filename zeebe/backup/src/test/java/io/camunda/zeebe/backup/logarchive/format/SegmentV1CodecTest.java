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

import com.github.luben.zstd.Zstd;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SegmentV1CodecTest {

  private static final SegmentIdentity IDENTITY = new SegmentIdentity("cluster-1", "default", 3);

  /** Every third entry (index % 3 == 0) has no positions; the others hold two. */
  private static List<LogArchiveFrame> frames(final long firstIndex, final int count) {
    final List<LogArchiveFrame> result = new ArrayList<>();
    long position = 1000;
    for (int i = 0; i < count; i++) {
      final long index = firstIndex + i;
      if (index % 3 == 0) {
        result.add(
            new LogArchiveFrame(
                index,
                -1,
                -1,
                -1,
                -1,
                index * 7,
                TestJournalRecords.recordData(index, -1, 40 + i, (byte) i)));
      } else {
        result.add(
            new LogArchiveFrame(
                index,
                -1,
                position,
                position,
                -1,
                index * 7,
                TestJournalRecords.recordData(index, position, 40 + i, (byte) i)));
        position += 2;
      }
    }
    return result;
  }

  private static LogArchiveFrame withHighest(final LogArchiveFrame f, final long highest) {
    return new LogArchiveFrame(
        f.index(),
        f.term(),
        f.asqn(),
        f.lowestPosition(),
        highest,
        f.checksum(),
        f.serializedRecord());
  }

  /** Frames as the archiver hands them over: application entries know their highest position. */
  private static List<LogArchiveFrame> archiverFrames(final long firstIndex, final int count) {
    return frames(firstIndex, count).stream()
        .map(f -> f.lowestPosition() >= 0 ? withHighest(f, f.lowestPosition() + 1) : f)
        .toList();
  }

  private static ByteBuffer le(final byte[] bytes, final int offset, final int length) {
    return ByteBuffer.wrap(bytes, offset, length).slice().order(ByteOrder.LITTLE_ENDIAN);
  }

  @Test
  void shouldRoundTripMixedEntries() {
    // given
    final var frames = archiverFrames(10, 20);

    // when
    final var encoded = SegmentV1Encoder.encode(IDENTITY, "tl-0", frames, 5_000);
    final var decoded = SegmentV1Decoder.decode(encoded.bytes());

    // then
    assertThat(decoded.frames())
        .extracting(
            LogArchiveFrame::index,
            LogArchiveFrame::asqn,
            LogArchiveFrame::checksum,
            LogArchiveFrame::serializedRecord)
        .containsExactlyElementsOf(
            frames.stream()
                .map(
                    f ->
                        org.assertj.core.groups.Tuple.tuple(
                            f.index(), f.asqn(), f.checksum(), f.serializedRecord()))
                .toList());
    assertThat(decoded.footer()).isEqualTo(encoded.footer());
    assertThat(decoded.identity()).isEqualTo(IDENTITY);
    assertThat(decoded.footer().entryCount()).isEqualTo(20);
    assertThat(encoded.sha256()).hasSize(64);
  }

  @Test
  void shouldWriteFooterOfSixtySixLittleEndianBytes() {
    // given
    final var frames = archiverFrames(9, 4); // first and last entry have no positions

    // when
    final var bytes = SegmentV1Encoder.encode(IDENTITY, "tl-0", frames, 1_234).bytes();

    // then
    final int footerStart = bytes.length - 66;
    final var history = ByteBuffer.wrap(bytes, footerStart, 16);
    final var expectedHistory =
        UUID.nameUUIDFromBytes("log-archive:tl-0".getBytes(StandardCharsets.UTF_8));
    assertThat(new UUID(history.getLong(), history.getLong())).isEqualTo(expectedHistory);
    final var footer = le(bytes, footerStart + 16, 50);
    assertThat(footer.getLong()).as("first index").isEqualTo(9);
    assertThat(footer.getLong()).as("last index").isEqualTo(12);
    assertThat(footer.getLong()).as("first position").isEqualTo(1000);
    assertThat(footer.getLong()).as("last position").isEqualTo(1003);
    assertThat(footer.getLong()).as("latest timestamp").isEqualTo(1_234);
    final long bodyLength = footer.getLong();
    assertThat(footer.get()).as("level").isZero();
    assertThat(footer.get()).as("format version").isEqualTo((byte) 1);
    assertThat(bytes[bytes.length - 1]).isEqualTo((byte) 1);
    // little-endian: the first index 9 is the low byte first
    assertThat(bytes[footerStart + 16]).isEqualTo((byte) 9);
    assertThat(bytes[footerStart + 17]).isZero();

    // the metadata runs from the end of the body to the footer
    assertThat(bodyLength).isPositive().isLessThan(footerStart);
    final int metadataLength = footerStart - (int) bodyLength;
    assertThat(metadataLength).isEqualTo(24 + 16 + 2 * 10 + 4);
  }

  @Test
  void shouldWriteIdentityFrameIndexAndDirectoryInMetadata() {
    // given
    final var frames = archiverFrames(9, 4);

    // when
    final var bytes = SegmentV1Encoder.encode(IDENTITY, "tl-0", frames, 1_234).bytes();

    // then
    final int footerStart = bytes.length - 66;
    final int bodyLength = (int) le(bytes, footerStart + 16 + 40, 8).getLong();
    final var metadata = le(bytes, bodyLength, footerStart - bodyLength);
    final int length = footerStart - bodyLength;
    assertThat(metadata.getInt(length - 4)).as("number of entries").isEqualTo(2);

    final int directory = length - 4 - 2 * 10;
    assertThat(metadata.getShort(directory)).as("key 1").isEqualTo((short) 1);
    assertThat(metadata.getInt(directory + 2)).as("identity offset").isZero();
    final int identityLength = metadata.getInt(directory + 6);
    assertThat(identityLength).isEqualTo(2 + 9 + 2 + 7 + 4);
    assertThat(metadata.getShort(directory + 10)).as("key 2").isEqualTo((short) 2);
    assertThat(metadata.getInt(directory + 12)).as("frame index offset").isEqualTo(identityLength);
    assertThat(metadata.getInt(directory + 16)).as("frame index length").isEqualTo(16);
    assertThat(directory).isEqualTo(identityLength + 16);

    // identity: cluster id, tenant, partition
    assertThat(metadata.getShort(0)).isEqualTo((short) 9);
    assertThat(new String(bytes, bodyLength + 2, 9, StandardCharsets.UTF_8)).isEqualTo("cluster-1");
    assertThat(metadata.getShort(11)).isEqualTo((short) 7);
    assertThat(new String(bytes, bodyLength + 13, 7, StandardCharsets.UTF_8)).isEqualTo("default");
    assertThat(metadata.getInt(20)).isEqualTo(3);

    // frame index: one frame, first position of the frame, offset 0
    assertThat(metadata.getLong(identityLength)).isEqualTo(1000);
    assertThat(metadata.getLong(identityLength + 8)).isZero();
  }

  @Test
  void shouldWriteOneZstdFrameWithContentSizeAndWithoutChecksum() {
    // given
    final var frames = archiverFrames(1, 30);

    // when
    final var encoded = SegmentV1Encoder.encode(IDENTITY, "tl-0", frames, 1);

    // then
    final byte[] body = Arrays.copyOfRange(encoded.bytes(), 0, (int) encoded.footer().bodyLength());
    assertThat(Zstd.getFrameContentSize(body)).isPositive();
    // zstd magic number 0xFD2FB528, little-endian
    assertThat(body[0]).isEqualTo((byte) 0x28);
    assertThat(body[1]).isEqualTo((byte) 0xB5);
    assertThat(body[2]).isEqualTo((byte) 0x2F);
    assertThat(body[3]).isEqualTo((byte) 0xFD);
    // frame header descriptor: bit 2 is the content checksum flag
    assertThat(body[4] & 0b100).isZero();
  }

  @Test
  void shouldKeepEntriesWithoutPositionsAndRecordNoTimestamp() {
    // given
    final var entry =
        new LogArchiveFrame(
            3, -1, -1, -1, -1, 9, TestJournalRecords.recordData(3, -1, 60, (byte) 1));

    // when
    final var encoded = SegmentV1Encoder.encode(IDENTITY, "tl-0", List.of(entry), -1);
    final var decoded = SegmentV1Decoder.decode(encoded.bytes());

    // then
    assertThat(decoded.footer().firstPosition()).isEqualTo(-1);
    assertThat(decoded.footer().lastPosition()).isEqualTo(-1);
    assertThat(decoded.footer().latestTimestamp()).isEqualTo(-1);
    assertThat(decoded.frameFirstPositions()).containsExactly(-1L);
    assertThat(decoded.frames())
        .singleElement()
        .isEqualTo(new LogArchiveFrame(3, -1, -1, -1, -1, 9, entry.serializedRecord()));
  }

  @Test
  void shouldEncodeTheSameEntriesToTheSameBytes() {
    // given
    final var frames = archiverFrames(1, 50);

    // when
    final var first = SegmentV1Encoder.encode(IDENTITY, "tl-0", frames, 77);
    final var second = SegmentV1Encoder.encode(IDENTITY, "tl-0", frames, 77);

    // then
    assertThat(first.bytes()).isEqualTo(second.bytes());
    assertThat(first.sha256()).isEqualTo(second.sha256());
  }

  @Test
  void shouldRejectEmptyFramesGapsAndDecreasingAsqn() {
    final var frames = archiverFrames(1, 3);
    assertThatThrownBy(() -> SegmentV1Encoder.encode(IDENTITY, "tl-0", List.of(), -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                SegmentV1Encoder.encode(
                    IDENTITY, "tl-0", List.of(frames.get(0), frames.get(2)), -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contiguous");
    final var decreasing =
        List.of(
            frames.get(0),
            new LogArchiveFrame(
                2, -1, 1, 1, 1, 0, TestJournalRecords.recordData(2, 1, 40, (byte) 0)));
    assertThatThrownBy(() -> SegmentV1Encoder.encode(IDENTITY, "tl-0", decreasing, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("asqn must increase");
  }

  @Test
  void shouldRejectTooShortObject() {
    assertThatThrownBy(() -> SegmentV1Decoder.decode(new byte[10]))
        .isInstanceOf(LogObjectFormatException.class)
        .hasMessageContaining("truncated footer");
  }

  @Test
  void shouldRejectUnknownFooterVersion() {
    // given
    final var bytes = valid();
    bytes[bytes.length - 1] = 2;

    // when / then
    assertThatThrownBy(() -> SegmentV1Decoder.decode(bytes))
        .isInstanceOf(LogObjectFormatException.class)
        .hasMessageContaining("version");
  }

  @Test
  void shouldRejectBodyLengthBeyondTheObject() {
    // given
    final var bytes = valid();
    le(bytes, bytes.length - 66 + 16 + 40, 8).putLong(bytes.length);

    // when / then
    assertThatThrownBy(() -> SegmentV1Decoder.decode(bytes))
        .isInstanceOf(LogObjectFormatException.class)
        .hasMessageContaining("body length");
  }

  @Test
  void shouldRejectInsaneDirectory() {
    // given: the entry count sits in the last four bytes before the footer
    final var bytes = valid();
    le(bytes, bytes.length - 66 - 4, 4).putInt(1_000_000);

    // when / then
    assertThatThrownBy(() -> SegmentV1Decoder.decode(bytes))
        .isInstanceOf(LogObjectFormatException.class)
        .hasMessageContaining("directory");
  }

  @Test
  void shouldRejectDirectoryEntryOutsideTheMetadata() {
    // given: the length of the frame index entry, 4 + 10 bytes before the footer
    final var bytes = valid();
    le(bytes, bytes.length - 66 - 4 - 4, 4).putInt(1 << 20);

    // when / then
    assertThatThrownBy(() -> SegmentV1Decoder.decode(bytes))
        .isInstanceOf(LogObjectFormatException.class)
        .hasMessageContaining("outside the metadata");
  }

  @Test
  void shouldRejectCorruptBody() {
    // given
    final var bytes = valid();
    for (int i = 5; i < 20; i++) {
      bytes[i] = (byte) ~bytes[i];
    }

    // when / then
    assertThatThrownBy(() -> SegmentV1Decoder.decode(bytes))
        .isInstanceOf(LogObjectFormatException.class);
  }

  @Test
  void shouldRejectFooterThatDisagreesWithTheBody() {
    // given: claim one entry more than the body holds
    final var bytes = valid();
    final int lastIndexOffset = bytes.length - 66 + 16 + 8;
    final var buf = le(bytes, lastIndexOffset, 8);
    buf.putLong(buf.getLong(0) + 1);

    // when / then
    assertThatThrownBy(() -> SegmentV1Decoder.decode(bytes))
        .isInstanceOf(LogObjectFormatException.class)
        .hasMessageContaining("entries");
  }

  @Test
  void shouldRejectTruncatedObject() {
    // given
    final var bytes = valid();
    final var truncated = Arrays.copyOfRange(bytes, 30, bytes.length);

    // when / then
    assertThatThrownBy(() -> SegmentV1Decoder.decode(truncated))
        .isInstanceOf(LogObjectFormatException.class);
  }

  private static byte[] valid() {
    return SegmentV1Encoder.encode(IDENTITY, "tl-0", archiverFrames(1, 12), 1).bytes().clone();
  }
}
