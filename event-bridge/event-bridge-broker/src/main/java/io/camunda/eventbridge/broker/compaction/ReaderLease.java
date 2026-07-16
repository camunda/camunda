/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * A single reader's hold on a clean segment file, carrying an <em>already-open</em> {@link
 * FileChannel} — the journal's lease shape, where the handle travels with the lease so the holder
 * can never reference bytes it cannot read. While the lease is outstanding, the {@link TrashQueue}
 * may <em>condemn</em> the file (rename it to a {@code *-deleted} marker) but the marker's physical
 * unlink is deferred to the last release — and even after the unlink, this lease's open descriptor
 * keeps the bytes readable (POSIX semantics).
 *
 * <p>A lease is released exactly once (further releases are no-ops); releasing closes the channel
 * and, if this was the last hold on a condemned file, performs the deferred unlink. Safe to use
 * with try-with-resources.
 *
 * <p>Threading: owned by a single reader; acquire/release interleave safely with the cleaner
 * actor's drain (the underlying registry transitions are atomic).
 */
public final class ReaderLease implements AutoCloseable {

  private final ReaderLeaseRegistry registry;
  private final Path file;
  private final FileChannel channel;
  private boolean released;

  ReaderLease(final ReaderLeaseRegistry registry, final Path file, final FileChannel channel) {
    this.registry = registry;
    this.file = file;
    this.channel = channel;
  }

  /** The file this lease holds (its original, non-condemned path). */
  public Path file() {
    return file;
  }

  /**
   * The open read channel on the leased file. Valid until {@link #release()}; remains readable even
   * if the trash queue unlinks the file in the meantime.
   */
  public FileChannel channel() {
    return channel;
  }

  /**
   * Releases the lease: closes the channel, decrements the file's reader count, and performs the
   * deferred unlink if this was the last hold on a condemned file. Idempotent.
   */
  public void release() {
    if (released) {
      return;
    }
    released = true;
    try {
      channel.close();
    } catch (final IOException e) {
      // A failed close does not affect the refcount discipline; fall through to the release.
    }
    registry.release(file);
  }

  @Override
  public void close() {
    release();
  }
}
