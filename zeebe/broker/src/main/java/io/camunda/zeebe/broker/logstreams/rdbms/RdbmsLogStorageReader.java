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
import java.util.Collections;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the blocks of one partition's RDBMS log, a page of blocks per query.
 *
 * <p>Follows the log as it grows: once {@link #hasNext()} has returned false, a later call queries
 * again and sees blocks committed in the meantime. Not thread-safe, like the log stream readers it
 * serves.
 *
 * <p>A query that fails, as when the database restarts, reads as "nothing to read yet" rather than
 * an error: the reader keeps its place and asks again on the next call, and a failed seek is redone
 * then. Zeebe's log consumers expect reads not to fail, as they do not on a local journal, and one
 * failing while a partition prepares a transition would mark the partition dead.
 */
final class RdbmsLogStorageReader implements LogStorageReader {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsLogStorageReader.class);

  /** Anchor before every block, so the next page starts at the first one. */
  private static final long BEFORE_FIRST_BLOCK = Long.MIN_VALUE;

  private final LogBlockRepository repository;
  private final int pageSize;
  private final NavigableMap<Long, LogBlock> uncommitted;
  private final ArrayDeque<LogBlock> page = new ArrayDeque<>();
  private final UnsafeBuffer currentBlock = new UnsafeBuffer();

  /** Low position of the last block returned or queued; the next page starts after it. */
  private long anchor = BEFORE_FIRST_BLOCK;

  private boolean seekPending;
  private long pendingSeekPosition;
  private boolean failing;

  RdbmsLogStorageReader(final LogBlockRepository repository, final int pageSize) {
    this(repository, pageSize, Collections.emptyNavigableMap());
  }

  /**
   * @param uncommitted appended blocks not yet committed, by low position, read once the database
   *     has nothing more; a block must be removed only after it is committed
   */
  RdbmsLogStorageReader(
      final LogBlockRepository repository,
      final int pageSize,
      final NavigableMap<Long, LogBlock> uncommitted) {
    this.repository = repository;
    this.pageSize = pageSize;
    this.uncommitted = uncommitted;
  }

  @Override
  public void seek(final long position) {
    page.clear();
    anchor = BEFORE_FIRST_BLOCK;
    seekPending = position >= 0;
    pendingSeekPosition = position;
    completePendingSeek();
  }

  @Override
  public boolean hasNext() {
    if (!completePendingSeek()) {
      return false;
    }
    if (page.isEmpty()) {
      try {
        // memory before the database: a block that commits, and so leaves memory, in between is
        // then found in the database, where the other order could skip it
        final var notYetCommitted =
            uncommitted.tailMap(anchor, false).values().stream().limit(pageSize).toList();
        final var committed = repository.readBlocksAfter(anchor, pageSize);
        page.addAll(committed.isEmpty() ? notYetCommitted : committed);
        recovered();
      } catch (final LogBlockRepositoryException e) {
        failed(e);
        return false;
      }
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

  /**
   * @return false if the seek is still pending because the database could not be read
   */
  private boolean completePendingSeek() {
    if (!seekPending) {
      return true;
    }
    try {
      repository
          .findBlockAtOrBefore(pendingSeekPosition)
          .ifPresent(
              block -> {
                page.add(block);
                anchor = block.lowPosition();
              });
      seekPending = false;
      recovered();
      return true;
    } catch (final LogBlockRepositoryException e) {
      failed(e);
      return false;
    }
  }

  /** Warns once per outage, rather than on every one of the consumer's retries. */
  private void failed(final LogBlockRepositoryException error) {
    if (!failing) {
      failing = true;
      LOG.warn("Failed to read the RDBMS log, will read again on the next attempt", error);
    } else {
      LOG.debug("Failed to read the RDBMS log again", error);
    }
  }

  private void recovered() {
    if (failing) {
      failing = false;
      LOG.info("Reading the RDBMS log again");
    }
  }

  @Override
  public void close() {
    page.clear();
    currentBlock.wrap(0, 0);
  }
}
