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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import com.github.luben.zstd.ZstdOutputStream;
import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidBody;
import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidEntry;
import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidFooter;
import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.InvalidMetadata;
import io.camunda.zeebe.eventplane.segment.InvalidSegmentException.Truncated;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SegmentTest {

  private static final LogId LOG = new LogId("cluster-1", "default", 3);
  private static final UUID HISTORY = UUID.fromString("7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071");
  private static final long NOON = 1_792_152_000_000L;

  @TempDir Path directory;
  private RaftJournal journal;

  @BeforeEach
  void openJournal() {
    journal = new RaftJournal(directory);
  }

  @AfterEach
  void closeJournal() {
    journal.close();
  }

  @Test
  void aPublishersSegmentHoldsTheJournalsEntries() throws Exception {
    final var entries =
        List.of(
            journal.initial(),
            journal.records(3, 200),
            journal.configuration(),
            journal.records(2, 100));

    final var segment = write(entries, NOON);

    final var footer = Footer.read(tail(segment));
    assertThat(footer)
        .isEqualTo(new Footer(HISTORY, 1, 4, 1, 5, NOON, footer.bodyLength(), Level.PUBLISHER));
    final var metadata = Metadata.read(tail(segment), tailStart(segment), footer);
    assertThat(metadata).isEqualTo(new Metadata(LOG, List.of(new Frame(1, 0))));
    assertThat(read(segment, footer, 0)).isEqualTo(entries);
  }

  @Test
  void theBodyHoldsTheBytesTheJournalWrites() throws Exception {
    final var entries =
        List.of(journal.initial(), journal.records(3, 200), journal.configuration());

    final var segment = write(entries, NOON);

    final var footer = Footer.read(tail(segment));
    final var body = decompress(Arrays.copyOf(segment, (int) footer.bodyLength()));
    assertThat(journal.files()).hasSize(1);
    assertThat(indexOf(journal.files().getFirst(), body)).isPositive();
  }

  @Test
  void aRollupsFramesStartAtTheirFirstRecord() throws Exception {
    final var entries =
        List.of(
            journal.initial(),
            journal.records(3, 1000),
            journal.configuration(),
            journal.records(2, 1000),
            journal.initial());

    // The last frame holds no records.
    final var segment = writeRollup(entries);

    final var footer = Footer.read(tail(segment));
    final var frames = Metadata.read(tail(segment), tailStart(segment), footer).frames();
    assertThat(frames).extracting(Frame::firstPosition).containsExactly(1L, 4L, NO_POSITION);
    final var metadata = Metadata.read(tail(segment), tailStart(segment), footer);
    assertThat(metadata.startAfter(NO_POSITION)).isZero();
    assertThat(metadata.startAfter(2)).isZero();
    assertThat(metadata.startAfter(3)).isEqualTo(frames.get(1).offset());
    assertThat(metadata.startAfter(10)).isEqualTo(frames.get(1).offset());
    assertThat(read(segment, footer, frames.get(1).offset())).isEqualTo(entries.subList(2, 5));
    assertThat(read(segment, footer, frames.get(2).offset())).isEqualTo(entries.subList(4, 5));
  }

  @Test
  void aSegmentWithoutRecordsHasNoPositions() throws Exception {
    final var segment = write(List.of(journal.initial()), NO_POSITION);

    final var footer = Footer.read(tail(segment));
    assertThat(footer.hasRecords()).isFalse();
    assertThat(footer.firstPosition()).isEqualTo(NO_POSITION);
    assertThat(footer.lastPosition()).isEqualTo(NO_POSITION);
    final var metadata = Metadata.read(tail(segment), tailStart(segment), footer);
    assertThat(metadata.frames()).containsExactly(new Frame(NO_POSITION, 0));
    assertThat(metadata.startAfter(5)).isZero();
  }

  @Test
  void theTailCanStartAnywhereUpToTheMetadata() throws Exception {
    final var segment = write(List.of(journal.records(3, 200)), NOON);
    final int footerStart = segment.length - Footer.LENGTH;
    final var footer = Footer.read(suffix(segment, footerStart));
    final int bodyEnd = (int) footer.bodyLength();

    assertThat(Metadata.read(suffix(segment, bodyEnd), bodyEnd, footer))
        .isEqualTo(Metadata.read(ByteBuffer.wrap(segment), 0, footer));
    assertThatThrownBy(() -> Metadata.read(suffix(segment, footerStart), footerStart, footer))
        .isInstanceOfSatisfying(
            TailTooShortException.class,
            e -> assertThat(e.metadataStart()).isEqualTo(footer.bodyLength()));
  }

  @Test
  void readersSkipMetadataTheyDontKnow() throws Exception {
    final var frames = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
    frames.putLong(1).putLong(0);
    final var entries = new TreeMap<Integer, byte[]>();
    entries.put(1, LOG.encode());
    entries.put(2, frames.array());
    entries.put(99, new byte[] {1, 2, 3});
    final var footer = new Footer(HISTORY, 1, 1, 1, 1, NOON, 10, Level.PUBLISHER);
    final var tail = concat(new byte[10], Metadata.encode(entries), footer.encode());

    assertThat(Metadata.read(ByteBuffer.wrap(tail), 0, Footer.read(ByteBuffer.wrap(tail))))
        .isEqualTo(new Metadata(LOG, List.of(new Frame(1, 0))));
  }

  @Test
  void readersRejectAnotherFormatVersion() throws Exception {
    final var segment = write(List.of(journal.records(1, 10)), NOON);
    segment[segment.length - 1] = 2;

    assertThatThrownBy(() -> Footer.read(tail(segment)))
        .isInstanceOfSatisfying(
            InvalidFooter.UnknownVersion.class, e -> assertThat(e.version()).isEqualTo(2))
        .hasMessage("Expected segment format version 1, but got 2");
  }

  @Test
  void entriesAreEqualByTheirBytes() {
    final var entry = journal.records(2, 10);
    final var copy = new Entry(entry.checksum(), entry.serializedRecord().clone());

    assertThat(copy).isEqualTo(entry).hasSameHashCodeAs(entry);
    assertThat(journal.records(2, 10)).isNotEqualTo(entry);
    assertThat(entry)
        .hasToString(
            "Entry[index=1, firstPosition=1, lastPosition=2, bytes=%d]"
                .formatted(entry.serializedRecord().length));
  }

  @Test
  void anEntryMustMatchItsChecksum() {
    final var entry = journal.records(1, 10);

    assertThatThrownBy(() -> new Entry(entry.checksum() + 1, entry.serializedRecord()))
        .isInstanceOfSatisfying(
            InvalidEntry.ChecksumMismatch.class,
            e -> {
              assertThat(e.expected()).isEqualTo(entry.checksum() + 1);
              assertThat(e.actual()).isEqualTo(entry.checksum());
            });
  }

  @Test
  void anEntrysRaftEntryMustBeAsLongAsItSays() {
    final var entry = journal.records(1, 10);
    final var longer = Arrays.copyOf(entry.serializedRecord(), entry.serializedRecord().length + 1);
    final var crc = new CRC32C();
    crc.update(longer);
    final int raftEntryLength = entry.raftEntry().remaining();

    assertThatThrownBy(() -> new Entry(crc.getValue(), longer))
        .isInstanceOf(InvalidEntry.class)
        .hasMessage(
            "Expected journal entry 1's Raft entry to be %d bytes, as the entry says, but got %d"
                .formatted(raftEntryLength, raftEntryLength + 1));
  }

  @Test
  void readersRejectAnEntryCutShort() throws Exception {
    final var entry = journal.records(1, 100);
    final var segment = write(List.of(entry), NOON);
    final var body =
        decompress(Arrays.copyOf(segment, (int) Footer.read(tail(segment)).bodyLength()));
    final var cut = new ByteArrayOutputStream();
    try (var zstd = new ZstdOutputStream(cut)) {
      zstd.write(body, 0, body.length - 5);
    }

    assertThatThrownBy(() -> readAll(new ByteArrayInputStream(cut.toByteArray())))
        .isInstanceOf(Truncated.class)
        .hasMessage(
            "Expected a RecordData of %d bytes, but the body ends after %d"
                .formatted(entry.serializedRecord().length, entry.serializedRecord().length - 5));
  }

  @Test
  void readersRejectAnUnknownLevel() throws Exception {
    final var segment = write(List.of(journal.records(1, 10)), NOON);
    segment[segment.length - 2] = 7;

    assertThatThrownBy(() -> Footer.read(tail(segment)))
        .isInstanceOfSatisfying(
            InvalidFooter.UnknownLevel.class, e -> assertThat(e.level()).isEqualTo(7))
        .hasMessage("Expected segment level 0 or 1, but got 7");
  }

  @Test
  void readersRejectAFooterWhoseIndexesRunBackwards() throws Exception {
    final var segment = write(List.of(journal.records(1, 10)), NOON);
    // The last index, after the history and the first index.
    ByteBuffer.wrap(segment)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putLong(segment.length - Footer.LENGTH + 24, 0);

    assertThatThrownBy(() -> Footer.read(tail(segment)))
        .isInstanceOfSatisfying(
            InvalidFooter.Indexes.class,
            e -> {
              assertThat(e.firstIndex()).isEqualTo(1);
              assertThat(e.lastIndex()).isZero();
            })
        .hasMessage("Expected a segment's indexes to run from at least 1 up, but got 1 to 0");
  }

  @Test
  void aSegmentsPartsCheckTheirFieldsWhenMade() {
    assertThatThrownBy(() -> new Footer(HISTORY, 1, 1, 5, 4, NOON, 10, Level.PUBLISHER))
        .isInstanceOf(InvalidFooter.Positions.class);
    assertThatThrownBy(() -> new Metadata(LOG, List.of(new Frame(1, 8))))
        .isInstanceOf(InvalidMetadata.class)
        .hasMessage("Expected a segment's first frame at offset 0, but got 8");
    assertThatThrownBy(() -> new LogId("cluster-1", "default", 0))
        .isInstanceOf(InvalidMetadata.class)
        .hasMessage("Expected a partition of at least 1, but got 0");
  }

  @Test
  void aSegmentWithRecordsHasATimestamp() throws Exception {
    try (var writer = writer(new ByteArrayOutputStream())) {
      writer.append(journal.records(1, 10));

      assertThatIllegalArgumentException()
          .isThrownBy(() -> writer.finish(NO_POSITION))
          .withMessage(
              "Expected a latest record timestamp of at least 0 for a segment with records, but"
                  + " got -1");
    }
  }

  @Test
  void readersRejectALogIdThatIsntUtf8() {
    final byte[] notUtf8 = {'a', (byte) 0xFF, 'b'};
    final var badCluster = logId(notUtf8, "default".getBytes(StandardCharsets.UTF_8), 3);
    final var badTenant = logId("cluster-1".getBytes(StandardCharsets.UTF_8), notUtf8, 3);

    for (var logId : List.of(badCluster, badTenant)) {
      final var tail = tail(logId, frameIndex(new Frame(1, 0)), 10);
      assertThatThrownBy(
              () -> Metadata.read(ByteBuffer.wrap(tail), 0, Footer.read(ByteBuffer.wrap(tail))))
          .isInstanceOf(InvalidMetadata.class)
          .hasMessageStartingWith("Expected a log id's ids in UTF-8");
    }
  }

  @Test
  void readersRejectABodyThatEndsTooLateForTheMetadata() throws Exception {
    final var metadata = new Metadata(LOG, List.of(new Frame(1, 0)));
    final var valid = tail(LOG.encode(), frameIndex(new Frame(1, 0)), 10);
    final long footerStart = valid.length - Footer.LENGTH;

    for (long bodyLength : List.of(Long.MAX_VALUE, footerStart - 3)) {
      final var footer = new Footer(HISTORY, 1, 1, 1, 1, NOON, bodyLength, Level.PUBLISHER);
      assertThatThrownBy(() -> Metadata.read(ByteBuffer.wrap(valid), 0, footer))
          .isInstanceOf(InvalidMetadata.class)
          .hasMessage(
              "Expected the body to end by byte %d, 4 bytes before the footer, but it ends at %d"
                  .formatted(footerStart - 4, bodyLength));
    }
    // A tail whose buffer starts later reads the same.
    final var shifted = ByteBuffer.wrap(concat(new byte[5], valid)).position(5);
    assertThat(Metadata.read(shifted, 0, Footer.read(ByteBuffer.wrap(valid)))).isEqualTo(metadata);
  }

  @Test
  void readersRejectMetadataWithoutAFrameIndex() {
    final var entries = new TreeMap<Integer, byte[]>();
    entries.put(1, LOG.encode());
    final var footer = new Footer(HISTORY, 1, 1, 1, 1, NOON, 10, Level.PUBLISHER);
    final var tail = concat(new byte[10], Metadata.encode(entries), footer.encode());

    assertThatThrownBy(() -> Metadata.read(ByteBuffer.wrap(tail), 0, footer))
        .isInstanceOf(InvalidMetadata.class)
        .hasMessage("Expected metadata entry 2, the frame index, but got keys [1]");
  }

  @Test
  void readersRejectATruncatedBody() throws Exception {
    final var segment = write(List.of(journal.records(3, 200)), NOON);
    final var footer = Footer.read(tail(segment));
    final var truncated = Arrays.copyOf(segment, (int) footer.bodyLength() - 10);

    assertThatThrownBy(() -> readAll(new ByteArrayInputStream(truncated)))
        .isInstanceOf(InvalidBody.class)
        .hasMessage(
            "Expected a body of zstd frames with windows of at most 8 MiB, but zstd fails with:"
                + " Truncated source");
  }

  @Test
  void entriesMustBeConsecutive() throws Exception {
    final var first = journal.records(1, 10);
    journal.records(1, 10);
    final var third = journal.records(1, 10);

    try (var writer = writer(new ByteArrayOutputStream())) {
      writer.append(first);
      assertThatThrownBy(() -> writer.append(third))
          .isInstanceOf(EntryOutOfOrderException.class)
          .hasMessage("Expected an entry at index 2, but got 3");
      assertThatThrownBy(() -> writer.append(first))
          .isInstanceOf(EntryOutOfOrderException.class)
          .hasMessage("Expected an entry at index 2, but got 1");
    }
  }

  @Test
  void aSegmentHoldsAtLeastOneEntry() throws Exception {
    try (var writer = writer(new ByteArrayOutputStream())) {
      assertThatThrownBy(() -> writer.finish(NO_POSITION))
          .isInstanceOf(EmptySegmentException.class);
    }
  }

  /** A publisher's segment, written through the public constructor. */
  private static byte[] write(List<Entry> entries, long timestamp) throws IOException {
    final var out = new ByteArrayOutputStream();
    return write(out, writer(out), entries, timestamp);
  }

  /** A rollup whose frames each end after the entry that brings them to 500 bytes. */
  private static byte[] writeRollup(List<Entry> entries) throws IOException {
    final var out = new ByteArrayOutputStream();
    return write(out, new SegmentWriter(out, LOG, HISTORY, Level.ROLLUP, 500), entries, NOON);
  }

  private static byte[] write(
      ByteArrayOutputStream out, SegmentWriter segmentWriter, List<Entry> entries, long timestamp)
      throws IOException {
    try (var writer = segmentWriter) {
      for (var entry : entries) {
        writer.append(entry);
      }
      writer.finish(timestamp);
      assertThat(writer.size()).isEqualTo(out.size());
    }
    return out.toByteArray();
  }

  private static SegmentWriter writer(ByteArrayOutputStream out) throws IOException {
    return new SegmentWriter(out, LOG, HISTORY, Level.PUBLISHER);
  }

  /** The last 16 KiB, or all of a smaller object, as a reader first reads it. */
  private static ByteBuffer tail(byte[] segment) {
    return suffix(segment, (int) tailStart(segment));
  }

  private static ByteBuffer suffix(byte[] segment, int start) {
    return ByteBuffer.wrap(segment, start, segment.length - start).slice();
  }

  private static long tailStart(byte[] segment) {
    return Math.max(0, segment.length - Footer.USUAL_TAIL_LENGTH);
  }

  /** The entries from the frame at {@code offset} up to the body's end, as a ranged GET reads. */
  private static List<Entry> read(byte[] segment, Footer footer, long offset) throws Exception {
    final var range = Arrays.copyOfRange(segment, (int) offset, (int) footer.bodyLength());
    return readAll(new ByteArrayInputStream(range));
  }

  private static List<Entry> readAll(ByteArrayInputStream body) throws Exception {
    final var entries = new ArrayList<Entry>();
    try (var reader = new EntryReader(body)) {
      for (var entry = reader.next(); entry != null; entry = reader.next()) {
        entries.add(entry);
      }
    }
    return entries;
  }

  private static byte[] decompress(byte[] body) throws Exception {
    try (var zstd = new ZstdInputStreamNoFinalizer(new ByteArrayInputStream(body))) {
      return zstd.readAllBytes();
    }
  }

  private static int indexOf(byte[] haystack, byte[] needle) {
    outer:
    for (int i = 0; i <= haystack.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  /** The tail of a segment with a body of {@code bodyLength} zero bytes and this metadata. */
  private static byte[] tail(byte[] logId, byte[] frameIndex, int bodyLength) {
    final var entries = new TreeMap<Integer, byte[]>();
    entries.put(1, logId);
    entries.put(2, frameIndex);
    final var footer = new Footer(HISTORY, 1, 1, 1, 1, NOON, bodyLength, Level.PUBLISHER);
    return concat(new byte[bodyLength], Metadata.encode(entries), footer.encode());
  }

  private static byte[] logId(byte[] clusterId, byte[] tenantId, int partition) {
    return ByteBuffer.allocate(2 + clusterId.length + 2 + tenantId.length + 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putShort((short) clusterId.length)
        .put(clusterId)
        .putShort((short) tenantId.length)
        .put(tenantId)
        .putInt(partition)
        .array();
  }

  private static byte[] frameIndex(Frame... frames) {
    final var index = ByteBuffer.allocate(frames.length * 16).order(ByteOrder.LITTLE_ENDIAN);
    for (var frame : frames) {
      index.putLong(frame.firstPosition()).putLong(frame.offset());
    }
    return index.array();
  }

  private static byte[] concat(byte[]... parts) {
    final var out = new ByteArrayOutputStream();
    for (var part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }
}
