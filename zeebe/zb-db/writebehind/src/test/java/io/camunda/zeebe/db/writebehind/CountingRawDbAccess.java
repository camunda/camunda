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

/** Counts the reads that reach the storage. */
final class CountingRawDbAccess implements RawDbAccess {

  private final RawDbAccess delegate;
  private final AtomicLong gets = new AtomicLong();
  private final AtomicLong iterators = new AtomicLong();

  CountingRawDbAccess(final RawDbAccess delegate) {
    this.delegate = delegate;
  }

  long gets() {
    return gets.get();
  }

  long iterators() {
    return iterators.get();
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
    return delegate.newIterator();
  }

  @Override
  public RawBatch newBatch() {
    return delegate.newBatch();
  }

  @Override
  public void createCheckpoint(final File directory) {
    delegate.createCheckpoint(directory);
  }
}
