/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.store;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A minimal object store used by the log archive. Keys are relative to the store's base path and
 * use {@code /} as separator; the {@code log-archive/} prefix is part of the key. Writes are
 * unconditional: a put on an existing key overwrites it, and readers never observe a partially
 * written object.
 */
public interface LogArchiveStore {

  /** Writes {@code content} under {@code key}, replacing any existing object. */
  CompletableFuture<Void> put(String key, byte[] content);

  /** Uploads the contents of {@code file} under {@code key}, replacing any existing object. */
  CompletableFuture<Void> putFile(String key, Path file);

  /** Returns the object's bytes, or {@link Optional#empty()} if the key does not exist. */
  CompletableFuture<Optional<byte[]>> getIfExists(String key);

  /**
   * Returns the bytes in {@code [offset, offset + length)}. If the object is shorter, the returned
   * array contains only the available bytes (empty if {@code offset} is at or past the end). Fails
   * if the key does not exist.
   */
  CompletableFuture<byte[]> getRange(String key, long offset, int length);

  /** Downloads the object to {@code target}, replacing it. Fails if the key does not exist. */
  CompletableFuture<Void> getToFile(String key, Path target);

  /** Returns all keys starting with {@code prefix}, sorted lexicographically. */
  CompletableFuture<List<String>> list(String prefix);

  /**
   * Returns the names of the "folders" directly below {@code prefix}, sorted lexicographically: the
   * distinct first path segments of the keys that start with {@code prefix}, without the prefix and
   * without the trailing {@code /}. Objects directly below the prefix (without a further {@code /})
   * are not returned. Implementations list with a delimiter and never enumerate the objects below
   * the folders.
   *
   * <p>For {@code log-archive/} and keys {@code log-archive/tl-0/p1/_latest.json} and {@code
   * log-archive/tl-0.r5/p1/_latest.json} this returns {@code [tl-0, tl-0.r5]}.
   *
   * @param prefix must end with {@code /}
   */
  CompletableFuture<List<String>> listChildPrefixes(String prefix);

  CompletableFuture<Void> closeAsync();
}
