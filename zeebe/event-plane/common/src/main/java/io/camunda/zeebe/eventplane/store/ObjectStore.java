/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A bucket of objects that the Event Plane reads and writes, synchronous and thread-safe. Nothing
 * in it is conditional, and a listing may show an object late.
 *
 * <p>A key is a non-empty sequence of segments separated by {@code /}, none of them empty, {@code
 * .} or {@code ..}, so that every store can keep it as a file. Methods throw an {@link
 * InvalidObjectNameException} for any other key.
 */
public interface ObjectStore extends AutoCloseable {

  /** Where the store's keys start: {@code file:///…/} or {@code s3://bucket/prefix/}. */
  URI root();

  /** The URI of the object with this key. */
  default URI uri(final String key) {
    return URI.create(root() + key);
  }

  /** Writes the object, and replaces it if it exists. */
  void put(String key, byte[] content) throws IOException;

  /** The object's content, or empty if it doesn't exist. */
  Optional<byte[]> get(String key) throws IOException;

  /**
   * At most {@code length} bytes of the object from {@code offset} on, clipped at its end: empty if
   * the offset is at or after the end.
   *
   * @throws NoSuchObjectException if the object doesn't exist
   */
  byte[] getRange(String key, long offset, int length) throws IOException;

  /** The object's size and modification time, or empty if it doesn't exist. */
  Optional<StoredObject> head(String key) throws IOException;

  /**
   * The objects whose key starts with {@code prefix} and sorts after {@code startAfter}, if given,
   * in key order (the byte order of the UTF-8 key, see {@link KeyOrder}), at most {@code limit}.
   */
  List<StoredObject> list(String prefix, @Nullable String startAfter, int limit) throws IOException;

  /**
   * The "directories" directly below {@code prefix}, as full keys that end in {@code /}, sorted.
   * The prefix is empty or ends in {@code /}. A directory is there while an object has its key as a
   * prefix.
   */
  List<String> listPrefixes(String prefix) throws IOException;

  /** Deletes the object. It is fine if it doesn't exist. */
  void delete(String key) throws IOException;

  @Override
  default void close() throws IOException {}
}
