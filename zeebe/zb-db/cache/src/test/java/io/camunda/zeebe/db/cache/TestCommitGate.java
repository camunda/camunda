/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** A commit gate whose log positions the test moves by hand. */
final class TestCommitGate implements CommitGate {

  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  private final AtomicInteger committedPositionReads = new AtomicInteger();
  private volatile long lastWrittenPosition = -1;
  private volatile long committedPosition = -1;

  /** Pretends the stream processor wrote records up to {@code position}. */
  void write(final long position) {
    lastWrittenPosition = position;
  }

  /** Pretends the log committed the records up to {@code position}, and tells the listeners. */
  void commit(final long position) {
    committedPosition = position;
    listeners.forEach(Runnable::run);
  }

  List<Runnable> listeners() {
    return listeners;
  }

  /** Returns how often someone asked for the committed position. */
  int committedPositionReads() {
    return committedPositionReads.get();
  }

  @Override
  public long lastWrittenPosition() {
    return lastWrittenPosition;
  }

  @Override
  public long committedPosition() {
    committedPositionReads.incrementAndGet();
    return committedPosition;
  }

  @Override
  public void addCommitListener(final Runnable listener) {
    listeners.add(listener);
  }

  @Override
  public void removeCommitListener(final Runnable listener) {
    listeners.remove(listener);
  }
}
