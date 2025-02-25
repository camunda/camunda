/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.journal.util.MockJournalMetastore;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AutoClose;

/**
 * A small utility to create controllable {@link SegmentedJournal} with fixed size segments. Knowing
 * beforehand how many entries will be present per segment is very useful to assert predictable
 * results on journal operations.
 *
 * <p>The number of entries per segment is predicated on every entry having the same data, and thus
 * all entries having the same fixed size. Use {@link #entry()} when appending as data to ensure
 * this always holds.
 *
 * <p>Most external dependencies are reused (e.g. {@link JournalMetrics}, {@link
 * MockJournalMetastore}), but the {@link SegmentsManager} and {@link SegmentedJournal} are always
 * recreated. Make sure to close either of them after creation.
 *
 * <p>By default, the string "test" is the entry data, and there is one entry per segment.
 */
final class TestJournalFactory {
  private final MockJournalMetastore metaStore = new MockJournalMetastore();
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private final JournalMetrics metrics = new JournalMetrics(meterRegistry);
  private final JournalIndex index = new SparseJournalIndex(1);

  private final int maxEntryCount;
  private final DirectBuffer entryData;
  private final BufferWriter entry;
  private final int size;
  private final SegmentLoader loader;

  TestJournalFactory() {
    this(1);
  }

  TestJournalFactory(final int maxEntryCount) {
    this("test", maxEntryCount);
  }

  /**
   * A test factory with custom entry data (used to compute the size of the segment) and a custom
   * max entry count.
   *
   * @param data the entry data
   * @param maxEntryCount the max number of entries per segment
   */
  TestJournalFactory(final String data, final int maxEntryCount) {
    this(data, maxEntryCount, SegmentAllocator.noop());
  }

  TestJournalFactory(final String data, final int maxEntryCount, final SegmentAllocator allocator) {
    entryData = BufferUtil.wrapString(data);
    entry = new DirectBufferWriter().wrap(entryData);
    size = getSerializedSize(entryData);
    this.maxEntryCount = maxEntryCount;

    loader = new SegmentLoader(2L * maxSegmentSize(), metrics, allocator);
  }

  int serializedEntrySize() {
    return size;
  }

  BufferWriter entry() {
    return entry;
  }

  int maxSegmentSize() {
    return (maxEntryCount * serializedEntrySize())
        + SegmentDescriptorSerializer.currentEncodingLength();
  }

  SegmentLoader segmentLoader() {
    return loader;
  }

  MockJournalMetastore metaStore() {
    return metaStore;
  }

  SegmentsManager segmentsManager(final Path directory) {
    return segmentsManager(directory, segmentLoader());
  }

  SegmentsManager segmentsManager(final Path directory, final SegmentLoader loader) {
    return segmentsManager(directory, loader, metaStore);
  }

  SegmentsManager segmentsManager(
      final Path directory, final SegmentLoader loader, final JournalMetaStore metaStore) {
    return new SegmentsManager(
        index,
        maxSegmentSize(),
        directory.resolve("data").toFile(),
        "journal",
        loader,
        metrics,
        metaStore);
  }

  SegmentedJournal journal(final SegmentsManager segments) {
    final var segmentsFlusher = new SegmentsFlusher(metaStore);
    return new SegmentedJournal(index, segments, metrics, segmentsFlusher);
  }

  DirectBuffer entryData() {
    return entryData;
  }

  JournalMetrics metrics() {
    return metrics;
  }

  private int getSerializedSize(final DirectBuffer data) {
    final var record = new RecordData(1, 1, data);
    final var serializer = new SBESerializer();
    final ByteBuffer buffer = ByteBuffer.allocate(128);
    return serializer.writeData(record, new UnsafeBuffer(buffer), 0).get()
        + FrameUtil.getLength()
        + serializer.getMetadataLength();
  }
}
