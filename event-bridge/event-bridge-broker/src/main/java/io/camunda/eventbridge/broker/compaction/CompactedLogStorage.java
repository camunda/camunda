/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import io.atomix.raft.RaftCommitListener;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * Decorates a data partition's {@link LogStorage} for a {@code COMPACT} topic (event-bridge ADR
 * 0001, decision 9): every {@link #newReader} returns a {@link CompactedLogStorageReader} whose
 * {@code scan} serves positions at or below the cleaner point from the clean set and delegates
 * everything else — including every other {@link
 * io.camunda.zeebe.logstreams.storage.LogStorageReader} method — to the live log unchanged.
 * Appending is untouched; only the read path composites.
 *
 * <p>Also forwards {@link RaftCommitListener#onCommit} to the wrapped storage: {@code
 * AtomixLogStorage} (the only production delegate) is itself a commit listener the partition's raft
 * server registers directly, and that registration keys off the stored {@code LogStorage}'s dynamic
 * type ({@code instanceof RaftCommitListener}) rather than a concrete class, so wrapping it here
 * must not silently drop the registration.
 *
 * <p>A {@code DELETE}-policy partition never wraps its {@link LogStorage} in this decorator, so its
 * fetch behavior is byte-for-byte unaffected.
 */
public final class CompactedLogStorage implements LogStorage, RaftCommitListener {

  private final LogStorage delegate;
  private final ManifestStore manifestStore;
  private final ReaderLeaseRegistry leaseRegistry;
  private final Path compactionDirectory;

  /**
   * @param delegate the live-log storage this decorates
   * @param manifestStore the partition's committed compaction manifest seam
   * @param leaseRegistry the reader-lease registry the cleaner condemns clean segments through
   * @param compactionDirectory the directory clean segments live in
   */
  public CompactedLogStorage(
      final LogStorage delegate,
      final ManifestStore manifestStore,
      final ReaderLeaseRegistry leaseRegistry,
      final Path compactionDirectory) {
    this.delegate = delegate;
    this.manifestStore = manifestStore;
    this.leaseRegistry = leaseRegistry;
    this.compactionDirectory = compactionDirectory;
  }

  @Override
  public CompactedLogStorageReader newReader() {
    return new CompactedLogStorageReader(
        delegate.newReader(), manifestStore, leaseRegistry, compactionDirectory);
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final BufferWriter bufferWriter,
      final AppendListener listener) {
    delegate.append(lowestPosition, highestPosition, bufferWriter, listener);
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final ByteBuffer blockBuffer,
      final AppendListener listener) {
    delegate.append(lowestPosition, highestPosition, blockBuffer, listener);
  }

  @Override
  public void addCommitListener(final CommitListener listener) {
    delegate.addCommitListener(listener);
  }

  @Override
  public void removeCommitListener(final CommitListener listener) {
    delegate.removeCommitListener(listener);
  }

  @Override
  public void onCommit(final long index) {
    if (delegate instanceof final RaftCommitListener listener) {
      listener.onCommit(index);
    }
  }
}
