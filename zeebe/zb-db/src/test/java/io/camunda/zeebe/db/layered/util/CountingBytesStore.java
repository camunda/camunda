/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.util;

import io.camunda.zeebe.db.layered.BytesStore;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Counts delegate point reads and prefix scans, to prove which delegate accesses the layered
 * store's clean cache, negative cache and absence watermark elide.
 */
public final class CountingBytesStore implements BytesStore {

  private final BytesStore delegate;
  private int gets;
  private int scans;

  public CountingBytesStore(final BytesStore delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  /** The number of point reads that reached the delegate. */
  public int gets() {
    return gets;
  }

  /** The number of prefix scans that reached the delegate. */
  public int scans() {
    return scans;
  }

  @Override
  public byte[] get(final byte[] key) {
    gets++;
    return delegate.get(key);
  }

  @Override
  public void put(final byte[] key, final byte[] value) {
    delegate.put(key, value);
  }

  @Override
  public void delete(final byte[] key) {
    delegate.delete(key);
  }

  @Override
  public void prefixScan(final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
    scans++;
    delegate.prefixScan(prefix, visitor);
  }
}
