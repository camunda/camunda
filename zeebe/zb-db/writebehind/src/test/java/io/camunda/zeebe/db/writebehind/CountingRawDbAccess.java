/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbIterator;
import io.camunda.zeebe.db.RawDbAccess;
import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

/** Counts the reads that reach the storage, and can inject work when an iterator closes. */
final class CountingRawDbAccess implements RawDbAccess {

  private final RawDbAccess delegate;
  private final AtomicLong gets = new AtomicLong();
  private final AtomicLong iterators = new AtomicLong();
  private volatile Runnable afterIteratorClosed = () -> {};

  CountingRawDbAccess(final RawDbAccess delegate) {
    this.delegate = delegate;
  }

  long gets() {
    return gets.get();
  }

  long iterators() {
    return iterators.get();
  }

  /** Runs {@code action} once, when the next iterator that is opened is closed. */
  void afterNextIteratorClosed(final Runnable action) {
    afterIteratorClosed = action;
  }

  @Override
  public byte[] get(final byte[] key, final int keyLength) {
    gets.incrementAndGet();
    return delegate.get(key, keyLength);
  }

  @Override
  public boolean keyMayExist(final byte[] key, final int keyLength) {
    return delegate.keyMayExist(key, keyLength);
  }

  @Override
  public DbIterator newIterator() {
    iterators.incrementAndGet();
    final var iterator = delegate.newIterator();
    final var action = afterIteratorClosed;
    afterIteratorClosed = () -> {};
    return new ClosingIterator(iterator, action);
  }

  @Override
  public RawBatch newBatch() {
    return delegate.newBatch();
  }

  @Override
  public void createCheckpoint(final File directory) {
    delegate.createCheckpoint(directory);
  }

  private record ClosingIterator(DbIterator delegate, Runnable afterClose) implements DbIterator {

    @Override
    public void seek(final byte[] key) {
      delegate.seek(key);
    }

    @Override
    public void seekForPrev(final byte[] key) {
      delegate.seekForPrev(key);
    }

    @Override
    public boolean isValid() {
      return delegate.isValid();
    }

    @Override
    public void next() {
      delegate.next();
    }

    @Override
    public void prev() {
      delegate.prev();
    }

    @Override
    public byte[] key() {
      return delegate.key();
    }

    @Override
    public byte[] value() {
      return delegate.value();
    }

    @Override
    public int key(final byte[] buffer) {
      return delegate.key(buffer);
    }

    @Override
    public int value(final byte[] buffer) {
      return delegate.value(buffer);
    }

    @Override
    public void close() {
      delegate.close();
      afterClose.run();
    }
  }
}
