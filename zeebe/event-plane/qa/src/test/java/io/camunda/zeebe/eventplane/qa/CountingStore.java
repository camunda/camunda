/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.qa;

import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.jspecify.annotations.Nullable;

/**
 * Counts a store's requests by kind and by what they are for, as S3 bills them: PUT, COPY, POST and
 * LIST in one class, GET and HEAD in the other.
 */
final class CountingStore implements ObjectStore {

  private final ObjectStore delegate;
  private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();

  CountingStore(final ObjectStore delegate) {
    this.delegate = delegate;
  }

  /** The counts, as "KIND what". */
  Map<String, Long> counts() {
    final var snapshot = new TreeMap<String, Long>();
    counts.forEach((key, count) -> snapshot.put(key, count.sum()));
    return snapshot;
  }

  @Override
  public URI root() {
    return delegate.root();
  }

  @Override
  public void put(final String key, final byte[] content) throws IOException {
    count("PUT", key);
    delegate.put(key, content);
  }

  @Override
  public Optional<byte[]> get(final String key) throws IOException {
    count("GET", key);
    return delegate.get(key);
  }

  @Override
  public byte[] getRange(final String key, final long offset, final int length) throws IOException {
    count("GET", key);
    return delegate.getRange(key, offset, length);
  }

  @Override
  public Optional<StoredObject> head(final String key) throws IOException {
    count("HEAD", key);
    return delegate.head(key);
  }

  @Override
  public List<StoredObject> list(
      final String prefix, @Nullable final String startAfter, final int limit) throws IOException {
    count("LIST", prefix);
    return delegate.list(prefix, startAfter, limit);
  }

  @Override
  public List<String> listPrefixes(final String prefix) throws IOException {
    count("LIST", prefix.isEmpty() ? "/" : prefix);
    return delegate.listPrefixes(prefix);
  }

  @Override
  public void delete(final String key) throws IOException {
    count("DELETE", key);
    delegate.delete(key);
  }

  private void count(final String kind, final String key) {
    counts.computeIfAbsent(kind + " " + what(key), ignored -> new LongAdder()).increment();
  }

  /** What a key is for, from the object names of the contract. */
  private static String what(final String key) {
    if (key.startsWith("_groups/")) {
      if (key.contains("/workers/")) {
        return "heartbeats";
      }
      if (key.contains("/cursors/")) {
        return "cursors";
      }
      return "group";
    }
    if (key.endsWith(".seg") || key.matches(".*/[0-9a-f-]{36}/(\\d{20}.*)?")) {
      return "segments";
    }
    if (key.contains("/histories/")) {
      return "histories";
    }
    if (key.contains("/retention/")) {
      return "retention";
    }
    return "discovery";
  }
}
