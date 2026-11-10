/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;

/**
 * A {@link LogBlockRepository} with the same semantics as {@link JdbcLogBlockRepository}, kept in
 * memory, which can be told to fail appends and to hold them until released.
 */
final class InMemoryLogBlockRepository implements LogBlockRepository {

  private final TreeMap<Long, LogBlock> blocks = new TreeMap<>();
  private final List<List<LogBlock>> appendCalls = new ArrayList<>();
  private final Deque<Failure> plannedFailures = new ArrayDeque<>();
  private long term = -1;
  private boolean failAlways;
  private CountDownLatch gate = new CountDownLatch(0);
  private volatile boolean waitingAtGate;

  @Override
  public synchronized boolean claimTerm(final long term) {
    if (term < this.term) {
      return false;
    }
    this.term = term;
    return true;
  }

  @Override
  public void append(final long term, final List<LogBlock> blocks) {
    awaitGate();
    synchronized (this) {
      appendCalls.add(List.copyOf(blocks));
      if (term != this.term) {
        throw new TermMismatchException(1, term);
      }
      final var failure = failAlways ? Failure.BEFORE_COMMIT : plannedFailures.poll();
      if (failure == Failure.BEFORE_COMMIT) {
        throw new LogBlockRepositoryException("injected failure before commit");
      }
      if (blocks.stream().anyMatch(block -> this.blocks.containsKey(block.lowPosition()))) {
        throw new LogBlockRepositoryException("duplicate block");
      }
      blocks.forEach(block -> this.blocks.put(block.lowPosition(), block));
      if (failure == Failure.AFTER_COMMIT) {
        throw new LogBlockRepositoryException("injected failure after commit");
      }
    }
  }

  @Override
  public synchronized Optional<LogBlock> findBlockAtOrBefore(final long position) {
    return Optional.ofNullable(blocks.floorEntry(position)).map(entry -> entry.getValue());
  }

  @Override
  public synchronized List<LogBlock> readBlocksAfter(final long lowPosition, final int maxBlocks) {
    return blocks.tailMap(lowPosition, false).values().stream().limit(maxBlocks).toList();
  }

  synchronized List<List<LogBlock>> appendCalls() {
    return List.copyOf(appendCalls);
  }

  synchronized List<LogBlock> blocks() {
    return List.copyOf(blocks.values());
  }

  /** The next {@code count} appends fail without writing anything. */
  synchronized void failNextAppends(final int count) {
    for (int i = 0; i < count; i++) {
      plannedFailures.add(Failure.BEFORE_COMMIT);
    }
  }

  /** The next append writes its blocks, then fails as if the commit's acknowledgement was lost. */
  synchronized void failNextAppendAfterCommit() {
    plannedFailures.add(Failure.AFTER_COMMIT);
  }

  synchronized void failAllAppends() {
    failAlways = true;
  }

  /** Appends block until {@link #releaseAppends()}. */
  synchronized void holdAppends() {
    gate = new CountDownLatch(1);
  }

  synchronized void releaseAppends() {
    gate.countDown();
  }

  boolean isAppendWaiting() {
    return waitingAtGate;
  }

  private void awaitGate() {
    final CountDownLatch currentGate;
    synchronized (this) {
      currentGate = gate;
    }
    waitingAtGate = true;
    try {
      currentGate.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LogBlockRepositoryException("interrupted", e);
    } finally {
      waitingAtGate = false;
    }
  }

  private enum Failure {
    BEFORE_COMMIT,
    AFTER_COMMIT
  }
}
