/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.journal.util.MockJournalMetastore;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.IndexEntry;
import io.camunda.zeebe.util.IndexScanResult;
import io.camunda.zeebe.util.JournalIndexCursor;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the fetch scan ({@link io.camunda.zeebe.journal.JournalReader#scanIndex(long, int, long)})
 * through the public journal API, using a simple test batch format interpreted by a test {@link
 * JournalIndexCursor}.
 */
final class JournalIndexScanTest {

  private static final int BATCH_HEADER_LENGTH = Integer.BYTES + 2 * Long.BYTES;
  private static final int PAYLOAD_LENGTH = 8;
  private static final int BATCH_LENGTH = BATCH_HEADER_LENGTH + PAYLOAD_LENGTH;
  private static final int JOURNAL_INDEX_DENSITY = 5;
  private static final int LARGE_MAX_BYTES = 1024 * 1024;

  private @TempDir Path directory;

  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private final MockJournalMetastore metaStore = new MockJournalMetastore();
  private SegmentedJournal journal;

  @AfterEach
  void tearDown() {
    CloseHelper.quietClose(journal);
  }

  @Test
  void shouldServeBatchSliceContainingRequestedAsqnFromMidRange() throws IOException {
    // given - a record with two batches so the requested ASQN falls mid-record
    journal = openJournal(64);
    final var first = batch(1, 5);
    final var second = batch(6, 10);
    final var third = batch(11, 15);
    journal.append(1, block(first, second));
    journal.append(11, block(third));

    // when - scanning from an ASQN within the second batch of the first record
    final var result = journal.openReader().scanIndex(7, LARGE_MAX_BYTES, Long.MAX_VALUE);

    // then - the scan starts at the batch containing the ASQN and serves its exact bytes
    assertThat(result).isInstanceOf(IndexScanResult.Success.class);
    final var success = (IndexScanResult.Success) result;
    try (final CloseableSilently ignored = success.lease()) {
      assertThat(success.entries())
          .extracting(IndexEntry::lowestAsqn, IndexEntry::highestAsqn)
          .containsExactly(tuple(6L, 10L), tuple(11L, 15L));
      assertThat(readSlice(success, success.entries().get(0))).isEqualTo(encode(second));
      assertThat(readSlice(success, success.entries().get(1))).isEqualTo(encode(third));

      // and - the slice matches the batch bytes a normal reader decodes from the record
      assertThat(readSlice(success, success.entries().get(0)))
          .isEqualTo(decodedBatchBytes(success.entries().get(0).index(), 1));
    }
  }

  @Test
  void shouldSkipRecordsWithoutAsqn() {
    // given - an ASQN-less record (e.g. a configuration entry) between two application records
    journal = openJournal(64);
    journal.append(1, block(batch(1, 5)));
    journal.append(new DirectBufferWriter(new UnsafeBuffer("not-a-batch".getBytes())));
    journal.append(6, block(batch(6, 10)));

    // when
    final var result = journal.openReader().scanIndex(1, LARGE_MAX_BYTES, Long.MAX_VALUE);

    // then - the ASQN-less record is skipped and both batches are served
    assertThat(result).isInstanceOf(IndexScanResult.Success.class);
    final var success = (IndexScanResult.Success) result;
    try (final CloseableSilently ignored = success.lease()) {
      assertThat(success.entries())
          .extracting(IndexEntry::lowestAsqn, IndexEntry::highestAsqn)
          .containsExactly(tuple(1L, 5L), tuple(6L, 10L));
    }
  }

  @Test
  void shouldRespectMaxBytesAcrossRecords() {
    // given - five single-batch records of a known encoded length
    journal = openJournal(64);
    appendSingleBatchRecords(1, 5);

    // when - allowing exactly two batches worth of bytes
    final var result = journal.openReader().scanIndex(1, 2 * BATCH_LENGTH, Long.MAX_VALUE);

    // then
    assertThat(result).isInstanceOf(IndexScanResult.Success.class);
    final var success = (IndexScanResult.Success) result;
    try (final CloseableSilently ignored = success.lease()) {
      assertThat(success.entries()).extracting(IndexEntry::lowestAsqn).containsExactly(1L, 2L);
    }
  }

  @Test
  void shouldServeAtLeastOneBatchWhenMaxBytesIsSmallerThanABatch() {
    // given
    journal = openJournal(64);
    appendSingleBatchRecords(1, 3);

    // when - maxBytes is smaller than a single batch
    final var result = journal.openReader().scanIndex(1, 1, Long.MAX_VALUE);

    // then - the scan still serves the first batch
    assertThat(result).isInstanceOf(IndexScanResult.Success.class);
    final var success = (IndexScanResult.Success) result;
    try (final CloseableSilently ignored = success.lease()) {
      assertThat(success.entries()).extracting(IndexEntry::lowestAsqn).containsExactly(1L);
    }
  }

  @Test
  void shouldNotServeBatchesBeyondUpperBoundIndex() {
    // given - three records at indexes 1, 2 and 3
    journal = openJournal(64);
    appendSingleBatchRecords(1, 3);

    // when - bounding the scan at index 2 (e.g. the commit index)
    final var result = journal.openReader().scanIndex(1, LARGE_MAX_BYTES, 2);

    // then - the batch at index 3 is not served
    assertThat(result).isInstanceOf(IndexScanResult.Success.class);
    final var success = (IndexScanResult.Success) result;
    try (final CloseableSilently ignored = success.lease()) {
      assertThat(success.entries()).extracting(IndexEntry::lowestAsqn).containsExactly(1L, 2L);
    }
  }

  @Test
  void shouldReturnFutureOffsetWhenBatchIsBeyondUpperBoundIndex() {
    // given - a record at index 3 whose batch exists but is beyond the visibility bound
    journal = openJournal(64);
    appendSingleBatchRecords(1, 3);

    // when
    final var result = journal.openReader().scanIndex(3, LARGE_MAX_BYTES, 2);

    // then
    assertThat(result).isEqualTo(IndexScanResult.FutureOffset.INSTANCE);
  }

  @Test
  void shouldReturnEndOfLogWhenScanningPastHead() {
    // given
    journal = openJournal(64);
    appendSingleBatchRecords(1, 3);

    // when
    final var result = journal.openReader().scanIndex(4, LARGE_MAX_BYTES, Long.MAX_VALUE);

    // then
    assertThat(result).isEqualTo(IndexScanResult.EndOfLog.INSTANCE);
  }

  @Test
  void shouldReturnEndOfLogOnEmptyJournal() {
    // given
    journal = openJournal(64);

    // when
    final var result = journal.openReader().scanIndex(1, LARGE_MAX_BYTES, Long.MAX_VALUE);

    // then
    assertThat(result).isEqualTo(IndexScanResult.EndOfLog.INSTANCE);
  }

  @Test
  void shouldReturnTruncatedBelowFirstRetainedAsqn() {
    // given - the oldest retained batch starts at ASQN 100
    journal = openJournal(64);
    journal.append(100, block(batch(100, 105)));
    journal.append(106, block(batch(106, 110)));

    // when
    final var result = journal.openReader().scanIndex(50, LARGE_MAX_BYTES, Long.MAX_VALUE);

    // then
    assertThat(result).isEqualTo(IndexScanResult.Truncated.INSTANCE);
  }

  @Test
  void shouldReturnTruncatedAfterCompaction() {
    // given - a multi-segment journal compacted past the first segments
    journal = openJournal(2);
    appendSingleBatchRecords(1, 8);
    journal.deleteUntil(5);

    // when
    final var truncated = journal.openReader().scanIndex(1, LARGE_MAX_BYTES, Long.MAX_VALUE);
    final var retained = journal.openReader().scanIndex(5, LARGE_MAX_BYTES, Long.MAX_VALUE);

    // then - ASQNs before the retained log are truncated, the retained ones are served
    assertThat(truncated).isEqualTo(IndexScanResult.Truncated.INSTANCE);
    assertThat(retained).isInstanceOf(IndexScanResult.Success.class);
    final var success = (IndexScanResult.Success) retained;
    try (final CloseableSilently ignored = success.lease()) {
      assertThat(success.entries().get(0).lowestAsqn()).isEqualTo(5L);
    }
  }

  @Test
  void shouldServeScansFromASingleSegment() {
    // given - six records spread over three segments of two records each
    journal = openJournal(2);
    appendSingleBatchRecords(1, 6);

    // when - scanning with an effectively unlimited byte budget
    final var firstScan = journal.openReader().scanIndex(1, LARGE_MAX_BYTES, Long.MAX_VALUE);

    // then - the scan stops at the end of the serving segment
    assertThat(firstScan).isInstanceOf(IndexScanResult.Success.class);
    final var firstSuccess = (IndexScanResult.Success) firstScan;
    final long continueFrom;
    try (final CloseableSilently ignored = firstSuccess.lease()) {
      assertThat(firstSuccess.entries()).extracting(IndexEntry::lowestAsqn).containsExactly(1L, 2L);
      continueFrom = firstSuccess.entries().getLast().highestAsqn() + 1;
    }

    // and - a follow-up scan continues seamlessly in the next segment
    final var secondScan =
        journal.openReader().scanIndex(continueFrom, LARGE_MAX_BYTES, Long.MAX_VALUE);
    assertThat(secondScan).isInstanceOf(IndexScanResult.Success.class);
    final var secondSuccess = (IndexScanResult.Success) secondScan;
    try (final CloseableSilently ignored = secondSuccess.lease()) {
      assertThat(secondSuccess.entries())
          .extracting(IndexEntry::lowestAsqn)
          .containsExactly(3L, 4L);
    }
  }

  @Test
  void shouldServeSameSlicesAfterReopen() throws IOException {
    // given - a multi-segment journal and a scan result captured before restart
    journal = openJournal(2);
    appendSingleBatchRecords(1, 6);
    final var before = scanSnapshot(3);

    // when - reopening the journal, which starts with a cold in-memory index
    journal.close();
    journal = openJournal(2);
    final var after = scanSnapshot(3);

    // then - the scan serves the identical slices
    assertThat(after.entries()).isEqualTo(before.entries());
    assertThat(after.slices()).zipSatisfy(before.slices(), (a, b) -> assertThat(a).isEqualTo(b));
  }

  @Test
  void shouldNotWriteIndexFiles() throws IOException {
    // given
    journal = openJournal(2);
    appendSingleBatchRecords(1, 6);

    // when - even after serving a scan
    final var result = journal.openReader().scanIndex(1, LARGE_MAX_BYTES, Long.MAX_VALUE);
    ((IndexScanResult.Success) result).lease().close();

    // then - the journal directory contains only segment files
    try (final Stream<Path> files = Files.list(dataDirectory())) {
      assertThat(files).allSatisfy(path -> assertThat(path.toString()).endsWith(".log"));
    }
  }

  @Test
  void shouldDeleteLeftoverIndexSidecarFilesOnLoad() throws IOException {
    // given - a journal directory containing a fetch-index sidecar file from an older version
    journal = openJournal(2);
    appendSingleBatchRecords(1, 4);
    journal.close();
    final var leftover = dataDirectory().resolve("test-1.log.sidx");
    Files.createFile(leftover);

    // when
    journal = openJournal(2);

    // then
    assertThat(leftover).doesNotExist();
  }

  @Test
  void shouldReturnEndOfLogWhenJournalHasNoIndexCursor() {
    // given - a journal without an application-entry cursor (e.g. a regular Zeebe partition)
    journal = openJournal(64, null);
    journal.append(1, block(batch(1, 5)));

    // when
    final var result = journal.openReader().scanIndex(1, LARGE_MAX_BYTES, Long.MAX_VALUE);

    // then
    assertThat(result).isEqualTo(IndexScanResult.EndOfLog.INSTANCE);
  }

  // ---------------------------------------------------------------------------------------------
  // fixtures
  // ---------------------------------------------------------------------------------------------

  private SegmentedJournal openJournal(final int entriesPerSegment) {
    return openJournal(entriesPerSegment, TestBatchCursor::new);
  }

  private SegmentedJournal openJournal(
      final int entriesPerSegment, final @Nullable Supplier<JournalIndexCursor> cursorSupplier) {
    final var builder =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(dataDirectory().toFile())
            .withName("test")
            .withMetaStore(metaStore)
            .withJournalIndexDensity(JOURNAL_INDEX_DENSITY)
            .withMaxSegmentSize(
                entriesPerSegment * serializedSingleBatchRecordSize()
                    + SegmentDescriptorSerializer.currentEncodingLength());
    if (cursorSupplier != null) {
      builder.withIndexCursorSupplier(cursorSupplier);
    }
    return builder.build();
  }

  private Path dataDirectory() {
    return directory.resolve("data");
  }

  /** Appends one single-batch record per ASQN in the (inclusive) range. */
  private void appendSingleBatchRecords(final long fromAsqn, final long toAsqn) {
    for (long asqn = fromAsqn; asqn <= toAsqn; asqn++) {
      journal.append(asqn, block(batch(asqn, asqn)));
    }
  }

  private ScanSnapshot scanSnapshot(final long fromAsqn) throws IOException {
    final var result = journal.openReader().scanIndex(fromAsqn, LARGE_MAX_BYTES, Long.MAX_VALUE);
    assertThat(result).isInstanceOf(IndexScanResult.Success.class);
    final var success = (IndexScanResult.Success) result;
    try (final CloseableSilently ignored = success.lease()) {
      final var slices = new ArrayList<byte[]>();
      for (final var entry : success.entries()) {
        slices.add(readSlice(success, entry));
      }
      return new ScanSnapshot(List.copyOf(success.entries()), slices);
    }
  }

  /** Reads the raw bytes a scan entry points at, straight from the segment file channel. */
  private static byte[] readSlice(final IndexScanResult.Success success, final IndexEntry entry)
      throws IOException {
    final var buffer = ByteBuffer.allocate(entry.length());
    int totalRead = 0;
    while (totalRead < entry.length()) {
      final int read = success.channel().read(buffer, entry.position() + totalRead);
      assertThat(read).isPositive();
      totalRead += read;
    }
    return buffer.array();
  }

  /** Returns the bytes of the n-th (0-based) batch of the record's data, as a reader decodes it. */
  private byte[] decodedBatchBytes(final long recordIndex, final int batchOrdinal) {
    try (final var reader = journal.openReader()) {
      reader.seek(recordIndex);
      final var record = reader.next();
      assertThat(record.index()).isEqualTo(recordIndex);

      final DirectBuffer data = record.data();
      int offset = 0;
      for (int i = 0; i < batchOrdinal; i++) {
        offset += BATCH_HEADER_LENGTH + data.getInt(offset, ByteOrder.LITTLE_ENDIAN);
      }
      final int length = BATCH_HEADER_LENGTH + data.getInt(offset, ByteOrder.LITTLE_ENDIAN);
      final var bytes = new byte[length];
      data.getBytes(offset, bytes);
      return bytes;
    }
  }

  private static Batch batch(final long lowestAsqn, final long highestAsqn) {
    final var payload = new byte[PAYLOAD_LENGTH];
    // derive the payload from the range so every batch has distinguishable content
    ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).putLong(31 * lowestAsqn + highestAsqn);
    return new Batch(lowestAsqn, highestAsqn, payload);
  }

  private static byte[] encode(final Batch... batches) {
    int length = 0;
    for (final var batch : batches) {
      length += BATCH_HEADER_LENGTH + batch.payload().length;
    }

    final var buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
    for (final var batch : batches) {
      buffer.putInt(batch.payload().length);
      buffer.putLong(batch.lowestAsqn());
      buffer.putLong(batch.highestAsqn());
      buffer.put(batch.payload());
    }
    return buffer.array();
  }

  private static BufferWriter block(final Batch... batches) {
    return new DirectBufferWriter(new UnsafeBuffer(encode(batches)));
  }

  private static int serializedSingleBatchRecordSize() {
    final var serializer = new SBESerializer();
    final var scratch = new UnsafeBuffer(ByteBuffer.allocate(256));
    final var record = new RecordData(1, 1, new UnsafeBuffer(encode(batch(1, 1))));
    return serializer.writeData(record, scratch, 0).get()
        + FrameUtil.getLength()
        + serializer.getMetadataLength();
  }

  private record Batch(long lowestAsqn, long highestAsqn, byte[] payload) {}

  private record ScanSnapshot(List<IndexEntry> entries, List<byte[]> slices) {}

  /**
   * Interprets a record's data as a sequence of test batches, each encoded as {@code [int
   * payloadLength, long lowestAsqn, long highestAsqn, byte[payloadLength] payload]}, mirroring how
   * the event-bridge cursor exposes the batches of an application entry.
   */
  private static final class TestBatchCursor implements JournalIndexCursor {

    private DirectBuffer data;
    private int baseOffset;
    private int currentOffset;
    private int nextOffset;

    @Override
    public void wrap(final DirectBuffer data, final int offset) {
      this.data = data;
      baseOffset = offset;
      currentOffset = 0;
      nextOffset = 0;
    }

    @Override
    public boolean hasNext() {
      return data != null && nextOffset < data.capacity();
    }

    @Override
    public void next() {
      currentOffset = nextOffset;
      nextOffset += currentLength();
    }

    @Override
    public int currentOffset() {
      return baseOffset + currentOffset;
    }

    @Override
    public long currentLowestAsqn() {
      return data.getLong(currentOffset + Integer.BYTES, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public long currentHighestAsqn() {
      return data.getLong(currentOffset + Integer.BYTES + Long.BYTES, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public int currentLength() {
      return BATCH_HEADER_LENGTH + data.getInt(currentOffset, ByteOrder.LITTLE_ENDIAN);
    }
  }
}
