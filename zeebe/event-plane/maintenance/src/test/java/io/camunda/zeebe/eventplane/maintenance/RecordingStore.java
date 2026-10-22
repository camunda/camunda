/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A store that passes on to another, and notes each write. It can also act as if an object were
 * deleted between a listing and its GET, and as if listing failed.
 */
final class RecordingStore implements ObjectStore {

  private final ObjectStore delegate;
  private final List<String> writes = new ArrayList<>();
  private final Set<String> vanishing = new HashSet<>();
  private boolean failing;

  RecordingStore(final ObjectStore delegate) {
    this.delegate = delegate;
  }

  /** The PUTs and DELETEs so far, in order, as {@code PUT key} and {@code DELETE key}. */
  List<String> writes() {
    return List.copyOf(writes);
  }

  /** From now on, GET finds no object with this key, though it is listed. */
  void vanish(final String key) {
    vanishing.add(key);
  }

  /** From now on, every listing of prefixes fails. */
  void failListing() {
    failing = true;
  }

  @Override
  public URI root() {
    return delegate.root();
  }

  @Override
  public void put(final String key, final byte[] content) throws IOException {
    writes.add("PUT " + key);
    delegate.put(key, content);
  }

  @Override
  public Optional<byte[]> get(final String key) throws IOException {
    return vanishing.contains(key) ? Optional.empty() : delegate.get(key);
  }

  @Override
  public byte[] getRange(final String key, final long offset, final int length) throws IOException {
    return delegate.getRange(key, offset, length);
  }

  @Override
  public Optional<StoredObject> head(final String key) throws IOException {
    return delegate.head(key);
  }

  @Override
  public List<StoredObject> list(
      final String prefix, final @Nullable String startAfter, final int limit) throws IOException {
    return delegate.list(prefix, startAfter, limit);
  }

  @Override
  public List<String> listPrefixes(final String prefix) throws IOException {
    if (failing) {
      throw new IOException("Expected the listing to work, but it fails, as the test says");
    }
    return delegate.listPrefixes(prefix);
  }

  @Override
  public void delete(final String key) throws IOException {
    writes.add("DELETE " + key);
    delegate.delete(key);
  }
}
