/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks reader holds on clean segment files and interlocks them with the {@link TrashQueue}'s
 * deferred deletion, mirroring how the journal's segments solve the identical problem: deletion is
 * a two-step <em>condemn</em> (rename to a {@code *-deleted} marker) followed by a physical unlink
 * deferred until the reference count reaches zero, and a lease always travels with an
 * <em>already-open</em> file handle.
 *
 * <h3>Why acquire opens the file</h3>
 *
 * <p>A count-then-open lease is self-validating: {@link #acquire} first increments the count
 * atomically (a single {@code compute}), then opens the file under its original name. If the open
 * succeeds, the increment happened before the trash queue's post-condemn count re-check, so the
 * marker cannot be unlinked while the lease is held — and even once it eventually is, the open
 * descriptor keeps the bytes readable (the POSIX property the journal leans on). If the open fails
 * because the file was just condemned (renamed away), the acquire releases its count and returns
 * {@link Optional#empty()} — the retry signal: the caller re-resolves the segment from the latest
 * committed manifest. Either way a lease can never reference bytes it cannot read.
 *
 * <h3>The condemn/release interlock</h3>
 *
 * <p>{@link TrashQueue#drain()} condemns a file by renaming it to its marker name and then calling
 * {@code condemn}, which atomically re-checks the count: zero means the marker is unlinked
 * immediately; otherwise the marker path is recorded and the <em>last release unlinks it</em>. All
 * count transitions happen inside {@code ConcurrentHashMap.compute}, so there is no window in which
 * a concurrent reader and the drain can disagree about who deletes.
 *
 * <p>Threading: fully thread-safe. Readers acquire and release from any thread; the cleaner actor
 * drains concurrently.
 */
public final class ReaderLeaseRegistry {

  private final ConcurrentHashMap<Path, LeaseState> states = new ConcurrentHashMap<>();

  /**
   * Acquires a lease on {@code file} with an open read channel. The returned lease must be released
   * (or closed) exactly once when the reader is done; releasing closes the channel.
   *
   * @param file the clean segment file, under its original (non-condemned) name
   * @return a lease carrying an open {@link FileChannel}, or {@link Optional#empty()} if the file
   *     is gone (condemned by the trash queue, or never existed) — re-resolve from the latest
   *     committed manifest and retry
   */
  public Optional<ReaderLease> acquire(final Path file) {
    states.compute(
        file,
        (p, state) -> {
          final LeaseState s = state == null ? new LeaseState() : state;
          s.count++;
          return s;
        });
    final FileChannel channel;
    try {
      channel = FileChannel.open(file, StandardOpenOption.READ);
    } catch (final NoSuchFileException e) {
      // Condemned (renamed away) between our increment and the open — or never existed. Give the
      // count back; if we raced a condemn, this release performs the deferred marker unlink.
      release(file);
      return Optional.empty();
    } catch (final IOException e) {
      release(file);
      throw new UncheckedIOException("Failed to open clean segment " + file, e);
    }
    return Optional.of(new ReaderLease(this, file, channel));
  }

  /** Returns the number of outstanding leases on {@code file}. */
  public int leaseCount(final Path file) {
    final LeaseState state = states.get(file);
    return state == null ? 0 : state.count;
  }

  /**
   * Records that {@code file} has been condemned (already renamed to {@code condemnedPath}) and
   * atomically re-checks the lease count: if zero, the marker is unlinked now; otherwise the unlink
   * is deferred to the last {@link #release}.
   *
   * @param file the original file path the leases are keyed by
   * @param condemnedPath the marker path the file was renamed to
   * @return {@code true} if the marker was unlinked immediately, {@code false} if deferred
   */
  boolean condemn(final Path file, final Path condemnedPath) {
    final boolean[] unlinkNow = {false};
    states.compute(
        file,
        (p, state) -> {
          if (state == null || state.count == 0) {
            unlinkNow[0] = true;
            return null;
          }
          state.condemnedPath = condemnedPath;
          return state;
        });
    if (unlinkNow[0]) {
      deleteQuietly(condemnedPath);
    }
    return unlinkNow[0];
  }

  void release(final Path file) {
    final Path[] toUnlink = {null};
    states.compute(
        file,
        (p, state) -> {
          if (state == null || state.count == 0) {
            throw new IllegalStateException("Reader lease released more times than acquired: " + p);
          }
          state.count--;
          if (state.count == 0) {
            toUnlink[0] = state.condemnedPath;
            return null;
          }
          return state;
        });
    if (toUnlink[0] != null) {
      deleteQuietly(toUnlink[0]);
    }
  }

  private static void deleteQuietly(final Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (final IOException e) {
      // The marker stays behind; TrashQueue.recoverOrphans sweeps stray markers on the next pass.
    }
  }

  private static final class LeaseState {
    int count;
    Path condemnedPath;
  }
}
