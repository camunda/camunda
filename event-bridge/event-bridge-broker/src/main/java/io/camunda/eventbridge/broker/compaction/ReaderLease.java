/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.nio.file.Path;

/**
 * A single reader's hold on a clean segment file, modelled on the refcount discipline of the
 * zero-copy fetch path's {@code SharedFileRegion}: while any lease on a file is outstanding the
 * {@link TrashQueue} must not unlink it, even once the file is superseded and its replacement is
 * durable. A lease is released exactly once (further releases are no-ops), so it is safe to use
 * with try-with-resources.
 *
 * <p>Threading: safe to acquire and release from reader threads while the cleaner actor drains the
 * trash queue; the underlying counts are atomic.
 */
public final class ReaderLease implements AutoCloseable {

  private final ReaderLeaseRegistry registry;
  private final Path file;
  private boolean released;

  ReaderLease(final ReaderLeaseRegistry registry, final Path file) {
    this.registry = registry;
    this.file = file;
  }

  /** The file this lease holds. */
  public Path file() {
    return file;
  }

  /** Releases the lease, decrementing the file's reader count. Idempotent. */
  public void release() {
    if (released) {
      return;
    }
    released = true;
    registry.release(file);
  }

  @Override
  public void close() {
    release();
  }
}
