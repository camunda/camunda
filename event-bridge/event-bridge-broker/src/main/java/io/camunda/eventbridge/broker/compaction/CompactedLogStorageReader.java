/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.util.IndexEntry;
import io.camunda.zeebe.util.IndexScanResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;

/**
 * The composite fetch reader for a {@code COMPACT} partition (event-bridge ADR 0001, decision 9):
 * {@link #scan} serves a position at or below the committed manifest's cleaner point {@code C} from
 * the clean set, and everything else — every other method, and any position above {@code C} — flows
 * through the live-log {@code delegate} completely unchanged.
 *
 * <h3>The composite decision</h3>
 *
 * <ol>
 *   <li>No committed manifest yet, or {@code fromPosition > C}: delegate to the live log as today.
 *   <li>{@code fromPosition <= C}: binary-search the manifest for the covering segment ({@link
 *       CompactionManifest#floorSegmentIndex}); if none covers it, the position is below the clean
 *       set's start — {@link IndexScanResult.Truncated} (the out-of-range boundary moves from the
 *       raft log's start to the clean set's start, per decision 9).
 *   <li>From the covering segment onward, acquire a {@link ReaderLease} and scan for entries
 *       at/after {@code fromPosition} ({@link CleanSegmentScan}); an empty result tries the next
 *       segment (gap-skip across segment boundaries). If every segment up to the manifest's end
 *       yields nothing, the range {@code (fromPosition, C]} was entirely swept with no survivor —
 *       by definition the next surviving record is the first live-log record after {@code C}, so
 *       the scan falls through to {@code delegate.scan(C + 1, maxBytes)}.
 *   <li>A lease acquisition failing (the file was condemned between reading the manifest and
 *       opening it — {@link ReaderLeaseRegistry#acquire}'s retry signal) re-resolves the latest
 *       manifest and retries once; a second race in immediate succession surfaces as an error
 *       rather than looping forever, since a cleaner pass cannot legitimately supersede the same
 *       segment twice within one fetch call.
 * </ol>
 *
 * <p>Every response is served from a single segment (or the live log) — never a mix — mirroring the
 * journal's own single-segment-per-response rule, since {@link IndexScanResult.Success} carries one
 * {@link java.nio.channels.FileChannel} and one lease.
 */
final class CompactedLogStorageReader implements LogStorageReader {

  private final LogStorageReader delegate;
  private final ManifestStore manifestStore;
  private final ReaderLeaseRegistry leaseRegistry;
  private final Path compactionDirectory;

  CompactedLogStorageReader(
      final LogStorageReader delegate,
      final ManifestStore manifestStore,
      final ReaderLeaseRegistry leaseRegistry,
      final Path compactionDirectory) {
    this.delegate = delegate;
    this.manifestStore = manifestStore;
    this.leaseRegistry = leaseRegistry;
    this.compactionDirectory = compactionDirectory;
  }

  @Override
  public void seek(final long position) {
    delegate.seek(position);
  }

  @Override
  public boolean hasNext() {
    return delegate.hasNext();
  }

  @Override
  public DirectBuffer next() {
    return delegate.next();
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public IndexScanResult scan(final long fromPosition, final int maxBytes) {
    return scan(fromPosition, maxBytes, true);
  }

  private IndexScanResult scan(
      final long fromPosition, final int maxBytes, final boolean allowRetry) {
    final Optional<CompactionManifest> committed = manifestStore.latest();
    if (committed.isEmpty() || fromPosition > committed.get().cleanerPoint()) {
      return delegate.scan(fromPosition, maxBytes);
    }

    final CompactionManifest manifest = committed.get();
    final ScanAttempt attempt = scanCleanSet(manifest, fromPosition, maxBytes);
    if (attempt instanceof final ScanAttempt.Done done) {
      return done.result();
    }
    if (attempt == ScanAttempt.Signal.EXHAUSTED) {
      // Nothing survived in (fromPosition, C] — the next surviving record, if any, is the first
      // live-log record after C (raft may already have truncated the log at/below C).
      return delegate.scan(manifest.cleanerPoint() + 1, maxBytes);
    }
    // RETRY: a lease raced a condemn between resolving the manifest and opening the segment. Retry
    // once against a freshly-resolved manifest; tolerate a mid-request cleaner commit, never
    // surface
    // it as an error for data that exists.
    if (!allowRetry) {
      throw new IllegalStateException(
          "Clean segment condemned mid-fetch even after retrying against the latest manifest");
    }
    return scan(fromPosition, maxBytes, false);
  }

  private ScanAttempt scanCleanSet(
      final CompactionManifest manifest, final long fromPosition, final int maxBytes) {
    final int floorIndex = manifest.floorSegmentIndex(fromPosition);
    if (floorIndex < 0) {
      // fromPosition precedes every segment's firstPosition: below the clean set's start.
      return new ScanAttempt.Done(IndexScanResult.Truncated.INSTANCE);
    }

    final List<CleanSegment> segments = manifest.segments();
    for (int i = floorIndex; i < segments.size(); i++) {
      final ScanAttempt attempt = scanSegment(segments.get(i), fromPosition, maxBytes);
      if (attempt != ScanAttempt.Signal.EXHAUSTED) {
        return attempt; // Done or RETRY — either way, stop here
      }
      // This segment had nothing at/after fromPosition; the surviving record (if any) may be in the
      // next segment (a whole segment's worth of keys could have been superseded).
    }
    return ScanAttempt.Signal.EXHAUSTED;
  }

  private ScanAttempt scanSegment(
      final CleanSegment segment, final long fromPosition, final int maxBytes) {
    final Path file = compactionDirectory.resolve(segment.fileName());
    final Optional<ReaderLease> leaseOpt = leaseRegistry.acquire(file);
    if (leaseOpt.isEmpty()) {
      return ScanAttempt.Signal.RETRY;
    }
    final ReaderLease lease = leaseOpt.get();

    final List<IndexEntry> entries;
    try {
      entries = CleanSegmentScan.scan(lease.channel(), fromPosition, maxBytes);
    } catch (final IOException e) {
      lease.release();
      throw new UncheckedIOException("Failed to scan clean segment " + file, e);
    }

    if (entries.isEmpty()) {
      lease.release();
      return ScanAttempt.Signal.EXHAUSTED;
    }
    return new ScanAttempt.Done(
        new IndexScanResult.Success(lease.channel(), entries, lease::release));
  }

  /**
   * The outcome of attempting to scan the clean set for a fetch: either a terminal {@link Done}
   * result, or one of two continuation {@link Signal}s the caller acts on.
   */
  private sealed interface ScanAttempt {

    /** A terminal result — either a hit or an out-of-range {@code Truncated}. */
    record Done(IndexScanResult result) implements ScanAttempt {}

    /** A continuation signal (not a terminal result). */
    enum Signal implements ScanAttempt {
      /** Nothing survived up to the manifest's cleaner point; continue at {@code C + 1}. */
      EXHAUSTED,
      /** A lease raced a concurrent condemn; re-resolve the latest manifest and retry once. */
      RETRY
    }
  }
}
