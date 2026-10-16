/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;

/** A {@link LogArchiveStore} keeping all objects in memory. */
final class InMemoryLogArchiveStore implements LogArchiveStore {

  private final NavigableMap<String, byte[]> objects = new ConcurrentSkipListMap<>();

  @Override
  public CompletableFuture<Void> put(final String key, final byte[] content) {
    objects.put(key, content.clone());
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> putFile(final String key, final Path file) {
    try {
      return put(key, Files.readAllBytes(file));
    } catch (final IOException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public CompletableFuture<Optional<byte[]>> getIfExists(final String key) {
    return CompletableFuture.completedFuture(
        Optional.ofNullable(objects.get(key)).map(byte[]::clone));
  }

  @Override
  public CompletableFuture<byte[]> getRange(final String key, final long offset, final int length) {
    final var content = objects.get(key);
    if (content == null) {
      return CompletableFuture.failedFuture(new NoSuchElementException(key));
    }
    final int from = (int) Math.min(offset, content.length);
    final int to = (int) Math.min(offset + length, content.length);
    return CompletableFuture.completedFuture(Arrays.copyOfRange(content, from, to));
  }

  @Override
  public CompletableFuture<Void> getToFile(final String key, final Path target) {
    final var content = objects.get(key);
    if (content == null) {
      return CompletableFuture.failedFuture(new NoSuchElementException(key));
    }
    try {
      Files.write(target, content);
      return CompletableFuture.completedFuture(null);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public CompletableFuture<List<String>> list(final String prefix) {
    return CompletableFuture.completedFuture(
        List.copyOf(objects.subMap(prefix, true, prefix + Character.MAX_VALUE, false).keySet()));
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    return CompletableFuture.completedFuture(null);
  }

  void remove(final String key) {
    objects.remove(key);
  }

  void replace(final String key, final byte[] content) {
    if (objects.replace(key, content.clone()) == null) {
      throw new NoSuchElementException(key);
    }
  }

  byte[] get(final String key) {
    final var content = objects.get(key);
    if (content == null) {
      throw new NoSuchElementException(key);
    }
    return content.clone();
  }
}
