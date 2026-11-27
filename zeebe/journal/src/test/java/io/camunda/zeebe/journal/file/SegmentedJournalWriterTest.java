/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.StickySyncFailure;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.CloseHelper;
import org.agrona.IoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

final class SegmentedJournalWriterTest {
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TestJournalFactory journalFactory =
      new TestJournalFactory("data", 2, this::fillWithOnes);

  private SegmentsManager segments;
  private SegmentedJournalWriter writer;
  private final SegmentDescriptorSerializer descriptorSerializer =
      new SegmentDescriptorSerializerSbe();

  private void fillWithOnes(
      final FileChannel channel, final java.io.FileDescriptor fileDescriptor, final long size) {
    // Fill with ones to verify in tests that the append invalidates next entry by overwriting with
    // 0
    IoUtil.fill(channel, 0, size, (byte) 0xff);
  }

  @BeforeEach
  void beforeEach(final @TempDir Path tempDir) {
    segments = journalFactory.segmentsManager(tempDir);
    segments.open();
    writer =
        new SegmentedJournalWriter(segments, journalFactory.metaStore(), journalFactory.metrics());
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietClose(segments);
  }

  @Test
  void shouldResetLastFlushedIndexOnDeleteAfter() throws FlushException {
    // given
    writer.append(1, journalFactory.entry());
    writer.append(2, journalFactory.entry());
    writer.append(3, journalFactory.entry());
    writer.append(4, journalFactory.entry());
    writer.flush();

    // when
    writer.deleteAfter(2, false);

    // then
    assertThat(writer.getLastFlushedIndex()).isEqualTo(2L);
    assertThat(journalFactory.metaStore().loadLastFlushedIndex()).isEqualTo(2L);
  }

  @Test
  void shouldNotClaimUnflushedEntriesAsFlushedOnDeleteAfter() throws FlushException {
    // given - 1-3 are flushed (segment 1: 1-2, segment 2: 3); 4-6 are not (segment 2: 4,
    // segment 3: 5-6)
    writer.append(1, journalFactory.entry());
    writer.append(2, journalFactory.entry());
    writer.append(3, journalFactory.entry());
    writer.flush();
    writer.append(4, journalFactory.entry());
    writer.append(5, journalFactory.entry());
    writer.append(6, journalFactory.entry());

    // when - truncating into segment 3 keeps the unflushed entries 4 and 5
    writer.deleteAfter(5, false);

    // then - they are not claimed as flushed, so the next flush still covers segment 2
    assertThat(writer.getLastFlushedIndex()).isEqualTo(3L);
  }

  @Test
  void shouldResetLastFlushedIndexOnReset() throws FlushException {
    // given
    writer.append(1, journalFactory.entry());
    writer.append(2, journalFactory.entry());
    writer.flush();

    // when
    writer.reset(8);

    // then
    assertThat(writer.getLastFlushedIndex()).isEqualTo(7L);
    assertThat(journalFactory.metaStore().hasLastFlushedIndex()).isFalse();
  }

  @Test
  void shouldUpdateDescriptor() {
    // given
    while (segments.getFirstSegment() == segments.getLastSegment()) {
      writer.append(-1, journalFactory.entry());
    }
    final var lastIndexInFirstSegment = segments.getLastSegment().index() - 1;

    // when
    segments.close();
    segments.open();

    // then
    final SegmentDescriptor descriptor = segments.getFirstSegment().descriptor();
    assertThat(descriptor.lastIndex()).isEqualTo(lastIndexInFirstSegment);
    assertThat(descriptor.lastPosition()).isNotZero();
  }

  @Test
  void shouldResetToLastEntryEvenIfLastPositionInDescriptorIsIncorrect() throws IOException {
    // given
    while (segments.getFirstSegment() == segments.getLastSegment()) {
      writer.append(-1, journalFactory.entry());
    }
    final var lastIndexInFirstSegment = segments.getLastSegment().index() - 1;

    final var descriptor = segments.getFirstSegment().descriptor();
    final var firstSegment = segments.getFirstSegment();
    final var corruptedDescriptor =
        descriptor.withUpdatedIndices(
            descriptor.lastIndex(), firstSegment.descriptor().lastPosition() + 1);
    final var segmentFile = firstSegment.file().file();
    try (final FileChannel channel =
        FileChannel.open(segmentFile, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
      final MappedByteBuffer buffer =
          channel.map(MapMode.READ_WRITE, 0, descriptorSerializer.encodingLength());
      descriptorSerializer.writeTo(corruptedDescriptor, buffer);
    }

    // when
    segments.close();
    segments.open();

    // then
    assertThat(segments.getFirstSegment().lastIndex()).isEqualTo(lastIndexInFirstSegment);
  }

  @Test
  void shouldInvalidateNextEntryAfterAppend() {
    try (final SegmentedJournalReader reader =
        new SegmentedJournalReader(
            journalFactory.journal(segments), new JournalMetrics(meterRegistry), false)) {
      // when
      writer.append(-1, journalFactory.entry());

      // then
      assertThat(reader.hasNext()).isTrue();
      reader.next();
      assertThat(reader.hasNext()).describedAs("Second entry does not exists").isFalse();
    }
  }

  @Test
  void shouldInvalidateNextEntryAfterAppendingSerializedRecord(@TempDir final Path tempDir) {
    // given
    final var writtenRecord = writer.append(-1, journalFactory.entry());

    final var followerJournalFactory = new TestJournalFactory("data", 5, this::fillWithOnes);
    final var followerSegments = followerJournalFactory.segmentsManager(tempDir);
    followerSegments.open();
    final var followerWriter =
        new SegmentedJournalWriter(
            followerSegments, journalFactory.metaStore(), followerJournalFactory.metrics());

    try (final SegmentedJournalReader reader =
        new SegmentedJournalReader(
            followerJournalFactory.journal(followerSegments),
            new JournalMetrics(meterRegistry),
            false)) {
      // when
      final byte[] serializedRecord = BufferUtil.bufferAsArray(writtenRecord.serializedRecord());
      followerWriter.append(writtenRecord.checksum(), serializedRecord);

      // then
      assertThat(reader.hasNext()).isTrue();
      reader.next();
      assertThat(reader.hasNext()).describedAs("Second entry does not exists").isFalse();
    }

    followerSegments.close();
  }

  @Test
  void shouldFlushAllSegments() throws FlushException {
    // given
    writer.append(1, journalFactory.entry());
    writer.append(2, journalFactory.entry());
    writer.append(3, journalFactory.entry());
    writer.append(4, journalFactory.entry());

    // when
    writer.flush();

    // then

    assertThat(writer.getLastFlushedIndex()).isEqualTo(4L);
  }

  @Test
  void shouldStoreLastFlushedIndexOnPartialFlush() {
    // given

    final SortedMap<Long, FlushableSegment> sortedMap = new TreeMap<>();
    sortedMap.put(1L, new TestSegment(15, true));
    sortedMap.put(2L, new TestSegment(30, false));

    final var segments = Mockito.mock(SegmentsManager.class);
    when(segments.syncFailure()).thenReturn(new StickySyncFailure());
    final var segment = Mockito.mock(Segment.class);
    when(segment.index()).thenReturn(1L);
    when(segments.getLastSegment()).thenReturn(segment);
    when(segment.writer()).thenReturn(Mockito.mock(SegmentWriter.class));

    final var segmentJournal = journalFactory.journal(segments);
    when(segments.getFirstSegment()).thenReturn(segment);
    when(segments.getTailSegments(anyLong())).thenAnswer(invocation -> sortedMap);

    // when
    assertThatThrownBy(segmentJournal::flush);

    // then
    assertThat(journalFactory.metaStore().loadLastFlushedIndex()).isEqualTo(15L);
  }

  @Test
  void shouldStoreLastFlushedIndexOnPartialFailedFlush() {
    // given
    final var error = new FlushException(new IOException("Cannot allocate memory"));
    final SortedMap<Long, FlushableSegment> sortedMap = new TreeMap<>();
    sortedMap.put(1L, new TestSegment(15));
    sortedMap.put(2L, new TestSegment(30, error));

    final var segments = Mockito.mock(SegmentsManager.class);
    when(segments.syncFailure()).thenReturn(new StickySyncFailure());
    final var segment = Mockito.mock(Segment.class);
    when(segment.index()).thenReturn(1L);
    when(segments.getLastSegment()).thenReturn(segment);
    when(segment.writer()).thenReturn(Mockito.mock(SegmentWriter.class));

    final var segmentJournal = journalFactory.journal(segments);
    when(segments.getFirstSegment()).thenReturn(segment);
    when(segments.getTailSegments(anyLong())).thenAnswer(invocation -> sortedMap);

    // when
    assertThatCode(segmentJournal::flush).isSameAs(error);
    assertThatThrownBy(segmentJournal::flush);

    // then
    assertThat(journalFactory.metaStore().loadLastFlushedIndex()).isEqualTo(15L);
  }

  @Test
  void shouldFailEveryFlushAfterAFailedFlush() throws FlushException {
    // given - like a failed fsync, the segment reports its flush error only once
    final var error = new FlushException(new IOException("Input/output error"));
    final var failingSegment = Mockito.mock(FlushableSegment.class);
    when(failingSegment.lastIndex()).thenReturn(30L);
    Mockito.doThrow(error).doNothing().when(failingSegment).flush();
    final SortedMap<Long, FlushableSegment> sortedMap = new TreeMap<>();
    sortedMap.put(1L, new TestSegment(15));
    sortedMap.put(2L, failingSegment);
    final var segmentJournal = journalWithSegments(sortedMap, journalFactory.metaStore());
    assertThatCode(segmentJournal::flush).isSameAs(error);

    // when
    final var thrown = catchThrowable(segmentJournal::flush);

    // then - the flush is not retried, and nothing after the failed flush counts as flushed
    assertThat(thrown).isInstanceOf(FlushException.class).hasCause(error);
    Mockito.verify(failingSegment, Mockito.times(1)).flush();
    assertThat(journalFactory.metaStore().loadLastFlushedIndex()).isEqualTo(15L);
  }

  @Test
  void shouldFailEveryFlushAfterFailingToStoreLastFlushedIndex() {
    // given
    final var error = new UncheckedIOException(new IOException("Input/output error"));
    final var metaStore = Mockito.mock(JournalMetaStore.class);
    Mockito.doThrow(error).when(metaStore).storeLastFlushedIndex(anyLong());
    final SortedMap<Long, FlushableSegment> sortedMap = new TreeMap<>();
    sortedMap.put(1L, new TestSegment(15));
    final var segmentJournal = journalWithSegments(sortedMap, metaStore);
    assertThatThrownBy(segmentJournal::flush).isInstanceOf(FlushException.class).hasCause(error);

    // when
    final var thrown = catchThrowable(segmentJournal::flush);

    // then
    assertThat(thrown).isInstanceOf(FlushException.class).hasRootCause(error.getCause());
    Mockito.verify(metaStore, Mockito.times(1)).storeLastFlushedIndex(anyLong());
  }

  @Test
  void shouldNotFlushAgainWhileAnotherFlushFails() throws InterruptedException {
    // given - a flush, e.g. a delayed one, that is about to fail; like a failed fsync, the error is
    // reported once, and the next flush of the same segment would succeed without writing it
    final var error = new FlushException(new IOException("Input/output error"));
    final var flushes = new AtomicInteger();
    final var failing = new CountDownLatch(1);
    final var fail = new CountDownLatch(1);
    final FlushableSegment segment =
        new FlushableSegment() {
          @Override
          public long lastIndex() {
            return 30;
          }

          @Override
          public void flush() throws FlushException {
            if (flushes.incrementAndGet() == 1) {
              failing.countDown();
              awaitUninterruptibly(fail);
              throw error;
            }
          }
        };
    final SortedMap<Long, FlushableSegment> sortedMap = new TreeMap<>();
    sortedMap.put(1L, segment);
    final var segmentJournal = journalWithSegments(sortedMap, journalFactory.metaStore());
    final var firstResult = new AtomicReference<Throwable>();
    final var first = new Thread(() -> firstResult.set(catchThrowable(segmentJournal::flush)));
    first.start();
    failing.await();

    // when - another flush, e.g. the one when closing, starts before the first one failed
    final var secondResult = new AtomicReference<Throwable>();
    final var second = new Thread(() -> secondResult.set(catchThrowable(segmentJournal::flush)));
    second.start();
    while (second.getState() != Thread.State.WAITING && flushes.get() < 2) {
      Thread.onSpinWait();
    }
    fail.countDown();
    first.join();
    second.join();

    // then - it waits for the first one, and fails instead of syncing the same pages again
    assertThat(firstResult.get()).isSameAs(error);
    assertThat(secondResult.get()).isInstanceOf(FlushException.class).hasCause(error);
    assertThat(flushes).hasValue(1);
    assertThat(journalFactory.metaStore().loadLastFlushedIndex()).isNotEqualTo(30L);
  }

  private static void awaitUninterruptibly(final CountDownLatch latch) {
    try {
      latch.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private SegmentedJournal journalWithSegments(
      final SortedMap<Long, FlushableSegment> flushableSegments, final JournalMetaStore metaStore) {
    final var segments = Mockito.mock(SegmentsManager.class);
    when(segments.syncFailure()).thenReturn(new StickySyncFailure());
    final var segment = Mockito.mock(Segment.class);
    when(segment.index()).thenReturn(1L);
    when(segment.writer()).thenReturn(Mockito.mock(SegmentWriter.class));
    when(segments.getLastSegment()).thenReturn(segment);
    when(segments.getFirstSegment()).thenReturn(segment);
    when(segments.getTailSegments(anyLong())).thenAnswer(invocation -> flushableSegments);
    return new SegmentedJournal(
        new SparseJournalIndex(1),
        segments,
        journalFactory.metrics(),
        metaStore,
        new StickySyncFailure());
  }
}
