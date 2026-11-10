/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Reads the blocks of one partition's RDBMS log, a page of blocks per query.
 *
 * <p>Follows the log as it grows: once {@link #hasNext()} has returned false, a later call queries
 * again and sees blocks committed in the meantime. Not thread-safe, like the log stream readers it
 * serves.
 */
final class RdbmsLogStorageReader implements LogStorageReader {

  /** Anchor before every block, so the next page starts at the first one. */
  private static final long BEFORE_FIRST_BLOCK = Long.MIN_VALUE;

  private final LogBlockRepository repository;
  private final int pageSize;
  private final ArrayDeque<LogBlock> page = new ArrayDeque<>();
  private final UnsafeBuffer currentBlock = new UnsafeBuffer();

  /** Low position of the last block returned or queued; the next page starts after it. */
  private long anchor = BEFORE_FIRST_BLOCK;

  RdbmsLogStorageReader(final LogBlockRepository repository, final int pageSize) {
    this.repository = repository;
    this.pageSize = pageSize;
  }

  @Override
  public void seek(final long position) {
    page.clear();
    anchor = BEFORE_FIRST_BLOCK;
    if (position < 0) {
      return;
    }

    repository
        .findBlockAtOrBefore(position)
        .ifPresent(
            block -> {
              page.add(block);
              anchor = block.lowPosition();
            });
  }

  @Override
  public boolean hasNext() {
    if (page.isEmpty()) {
      page.addAll(repository.readBlocksAfter(anchor, pageSize));
      if (!page.isEmpty()) {
        anchor = page.getLast().lowPosition();
      }
    }
    return !page.isEmpty();
  }

  @Override
  public DirectBuffer next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    currentBlock.wrap(page.remove().data());
    return currentBlock;
  }

  @Override
  public void close() {
    page.clear();
    currentBlock.wrap(0, 0);
  }
}
