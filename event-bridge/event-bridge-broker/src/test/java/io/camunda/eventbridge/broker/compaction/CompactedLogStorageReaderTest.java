/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import static io.camunda.eventbridge.broker.compaction.CompactionRecords.put;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.util.IndexScanResult;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The composite fetch decision (event-bridge ADR 0001, decision 9): positions at or below the
 * committed manifest's cleaner point serve from the clean set; everything else, and anything the
 * clean set can't resolve, flows through the live-log delegate.
 */
final class CompactedLogStorageReaderTest {

  @TempDir Path dir;

  private final FakeLogStorageReader delegate = new FakeLogStorageReader();

  private CompactedLogStorageReader readerWith(final ManifestStore manifestStore) {
    return new CompactedLogStorageReader(delegate, manifestStore, new ReaderLeaseRegistry(), dir);
  }

  private CleanSegment writeSegment(final long cleanerPoint, final CompactionRecord... records) {
    final var writer = new CleanSegmentWriter(dir, cleanerPoint, 1 << 20);
    for (final CompactionRecord record : records) {
      writer.append(record);
    }
    return writer.finish().get(0);
  }

  @Test
  void shouldDelegateWhenNoManifestHasEverBeenCommitted() {
    // given a partition that has never run a cleaner pass yet
    final var reader = readerWith(new FixedManifestStore(Optional.empty()));
    delegate.nextResult = IndexScanResult.EndOfLog.INSTANCE;

    // when
    final var result = reader.scan(10, 100);

    // then
    assertThat(result).isSameAs(IndexScanResult.EndOfLog.INSTANCE);
    assertThat(delegate.lastFromPosition).isEqualTo(10);
  }

  @Test
  void shouldDelegateWhenPositionIsAboveTheCleanerPoint() {
    // given a committed manifest at C=5
    final var manifest = new CompactionManifest(CompactionManifest.VERSION_1, 5, 1, List.of());
    final var reader = readerWith(new FixedManifestStore(Optional.of(manifest)));
    delegate.nextResult = IndexScanResult.EndOfLog.INSTANCE;

    // when: fetching position 6, above C
    final var result = reader.scan(6, 100);

    // then: unchanged live-log path
    assertThat(result).isSameAs(IndexScanResult.EndOfLog.INSTANCE);
    assertThat(delegate.lastFromPosition).isEqualTo(6);
  }

  @Test
  void shouldReturnTruncatedBelowTheCleanSetStart() {
    // given a clean set starting at position 10
    final CleanSegment segment = writeSegment(20, put(10, "a", "va"));
    final var manifest =
        new CompactionManifest(CompactionManifest.VERSION_1, 20, 1, List.of(segment));
    final var reader = readerWith(new FixedManifestStore(Optional.of(manifest)));

    // when: requesting position 3, below the clean set's first record
    final var result = reader.scan(3, 100);

    // then
    assertThat(result).isEqualTo(IndexScanResult.Truncated.INSTANCE);
    assertThat(delegate.scanCalls).isZero(); // never fell through to the live log
  }

  @Test
  void shouldServeFromTheCleanSetWhenThePositionIsCovered() throws Exception {
    // given
    final CleanSegment segment = writeSegment(20, put(5, "a", "va"), put(10, "b", "vb"));
    final var manifest =
        new CompactionManifest(CompactionManifest.VERSION_1, 20, 1, List.of(segment));
    final var reader = readerWith(new FixedManifestStore(Optional.of(manifest)));

    // when
    final var result = reader.scan(7, Integer.MAX_VALUE);

    // then: gap-skips to the next surviving record and serves it from the clean segment
    assertThat(result).isInstanceOf(IndexScanResult.Success.class);
    final var success = (IndexScanResult.Success) result;
    assertThat(success.entries()).extracting(e -> e.lowestAsqn()).containsExactly(10L);
    assertThat(delegate.scanCalls).isZero();

    // and: releasing the lease closes the channel without error
    success.lease().release();
    assertThat(success.channel().isOpen()).isFalse();
  }

  @Test
  void shouldFallThroughToLiveLogWhenNothingSurvivedUpToTheCleanerPoint() {
    // given the only clean-set record is far below the requested position, and it is the last
    // (and only) segment — nothing in the clean set can satisfy the request
    final CleanSegment segment = writeSegment(20, put(2, "a", "va"));
    final var manifest =
        new CompactionManifest(CompactionManifest.VERSION_1, 20, 1, List.of(segment));
    final var reader = readerWith(new FixedManifestStore(Optional.of(manifest)));
    delegate.nextResult = IndexScanResult.EndOfLog.INSTANCE;

    // when: requesting position 15 (covered by the segment's floor, but swept away)
    final var result = reader.scan(15, 100);

    // then: falls through to the live log starting at cleanerPoint + 1
    assertThat(result).isSameAs(IndexScanResult.EndOfLog.INSTANCE);
    assertThat(delegate.lastFromPosition).isEqualTo(21);
  }

  @Test
  void shouldRetryOnceAgainstANewerManifestWhenALeaseRaceIsDetected() {
    // given the first manifest references a segment file that was never durably present (simulates
    // a condemn racing the lease acquire), and the second (retried) manifest's segment is real
    final var missingSegment = new CleanSegment("clean-0000000001-0000000020.log", 1, 10, 0);
    final var staleManifest =
        new CompactionManifest(CompactionManifest.VERSION_1, 20, 1, List.of(missingSegment));
    final CleanSegment realSegment = writeSegment(20, put(1, "a", "va"));
    final var freshManifest =
        new CompactionManifest(CompactionManifest.VERSION_1, 20, 1, List.of(realSegment));
    final var reader = readerWith(new SequencedManifestStore(staleManifest, freshManifest));

    // when
    final var result = reader.scan(1, Integer.MAX_VALUE);

    // then: the retry against the fresh manifest served the data — never an error
    assertThat(result).isInstanceOf(IndexScanResult.Success.class);
  }

  @Test
  void shouldFailAfterOneRetryIfTheRaceRepeats() {
    // given every manifest returned references a segment that doesn't exist
    final var missingSegment = new CleanSegment("clean-0000000001-0000000020.log", 1, 10, 0);
    final var manifest =
        new CompactionManifest(CompactionManifest.VERSION_1, 20, 1, List.of(missingSegment));
    final var reader = readerWith(new FixedManifestStore(Optional.of(manifest)));

    // when / then
    assertThatThrownBy(() -> reader.scan(1, Integer.MAX_VALUE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("even after retrying");
  }

  @Test
  void shouldGapSkipAcrossSegmentBoundaries() throws Exception {
    // given two segments from two passes: the first segment's only key was later superseded (no
    // survivor there), the second segment holds the surviving record
    final CleanSegment firstSegment = writeSegment(10, put(2, "a", "v1"));
    final CleanSegment secondSegment = writeSegment(20, put(15, "a", "v2"));

    final var manifest =
        new CompactionManifest(
            CompactionManifest.VERSION_1, 20, 1, List.of(firstSegment, secondSegment));
    final var reader = readerWith(new FixedManifestStore(Optional.of(manifest)));

    // when: requesting position 5 — swept in the first segment, present in the second
    final var result = reader.scan(5, Integer.MAX_VALUE);

    // then
    assertThat(result).isInstanceOf(IndexScanResult.Success.class);
    final var success = (IndexScanResult.Success) result;
    assertThat(success.entries()).extracting(e -> e.lowestAsqn()).containsExactly(15L);
    success.lease().release();
  }

  /** Always returns the same (possibly empty) manifest. */
  private record FixedManifestStore(Optional<CompactionManifest> committed)
      implements ManifestStore {
    @Override
    public void commit(final CompactionManifest toCommit, final List<Path> newFiles) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CompactionManifest> latest() {
      return committed;
    }
  }

  /** Returns each manifest in turn, then keeps returning the last one for subsequent calls. */
  private static final class SequencedManifestStore implements ManifestStore {
    private final Deque<CompactionManifest> queue;

    private SequencedManifestStore(final CompactionManifest... manifests) {
      queue = new ArrayDeque<>(List.of(manifests));
    }

    @Override
    public void commit(final CompactionManifest toCommit, final List<Path> newFiles) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CompactionManifest> latest() {
      return Optional.of(queue.size() > 1 ? queue.pollFirst() : queue.peekFirst());
    }
  }

  /** Records the last {@code scan} call and returns a canned result. */
  private static final class FakeLogStorageReader implements LogStorageReader {
    long lastFromPosition = -1;
    int scanCalls;
    IndexScanResult nextResult = IndexScanResult.EndOfLog.INSTANCE;

    @Override
    public void seek(final long position) {}

    @Override
    public IndexScanResult scan(final long fromPosition, final int maxBytes) {
      lastFromPosition = fromPosition;
      scanCalls++;
      return nextResult;
    }

    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public DirectBuffer next() {
      throw new java.util.NoSuchElementException();
    }

    @Override
    public void close() {}
  }
}
