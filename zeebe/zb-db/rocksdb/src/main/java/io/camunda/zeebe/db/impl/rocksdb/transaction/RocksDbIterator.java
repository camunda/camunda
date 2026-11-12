/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import org.rocksdb.RocksIterator;

/** {@link DbIterator} that delegates every call to a {@link RocksIterator}. */
public final class RocksDbIterator implements DbIterator {

  private final RocksIterator delegate;

  public RocksDbIterator(final RocksIterator delegate) {
    this.delegate = delegate;
  }

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
  }
}
