/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/** A store that records ranged GETs, and can do something before one, as maintenance would. */
final class RecordingStore implements ObjectStore {

  private final ObjectStore delegate;
  private final List<Range> ranges = new CopyOnWriteArrayList<>();
  private final List<Listing> listings = new CopyOnWriteArrayList<>();
  private volatile @Nullable BeforeRange beforeRange;

  RecordingStore(final ObjectStore delegate) {
    this.delegate = delegate;
  }

  void beforeRange(final BeforeRange hook) {
    beforeRange = hook;
  }

  List<Range> ranges() {
    return List.copyOf(ranges);
  }

  @Override
  public URI root() {
    return delegate.root();
  }

  @Override
  public void put(final String key, final byte[] content) throws IOException {
    delegate.put(key, content);
  }

  @Override
  public Optional<byte[]> get(final String key) throws IOException {
    return delegate.get(key);
  }

  @Override
  public byte[] getRange(final String key, final long offset, final int length) throws IOException {
    final var hook = beforeRange;
    if (hook != null) {
      hook.accept(key);
    }
    ranges.add(new Range(key, offset, length));
    return delegate.getRange(key, offset, length);
  }

  @Override
  public Optional<StoredObject> head(final String key) throws IOException {
    return delegate.head(key);
  }

  @Override
  public List<StoredObject> list(
      final String prefix, @Nullable final String startAfter, final int limit) throws IOException {
    listings.add(new Listing(prefix, startAfter));
    return delegate.list(prefix, startAfter, limit);
  }

  List<Listing> listings() {
    return List.copyOf(listings);
  }

  @Override
  public List<String> listPrefixes(final String prefix) throws IOException {
    return delegate.listPrefixes(prefix);
  }

  @Override
  public void delete(final String key) throws IOException {
    delegate.delete(key);
  }

  record Range(String key, long offset, int length) {}

  record Listing(String prefix, @Nullable String startAfter) {}

  @FunctionalInterface
  interface BeforeRange {
    void accept(String key) throws IOException;
  }
}
