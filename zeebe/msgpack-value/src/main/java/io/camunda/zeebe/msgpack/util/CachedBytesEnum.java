/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

@SuppressWarnings("unchecked")
public final class CachedBytesEnum<E extends Enum<E>> {

  private static final ConcurrentMap<Class<?>, CachedBytesEnum<?>> GLOBAL_CACHE =
      new ConcurrentHashMap<>();

  private final DirectBuffer[] valuesAsBuffers;
  private final AsciiTrieEnumParser<E> parser;

  private CachedBytesEnum(final Class<E> clazz) {
    valuesAsBuffers = byteRepresentations(clazz);
    parser = new AsciiTrieEnumParser<E>(clazz);
  }

  public static <E extends Enum<E>> CachedBytesEnum<E> get(final Class<E> clazz) {
    return (CachedBytesEnum<E>)
        GLOBAL_CACHE.computeIfAbsent(clazz, c -> new CachedBytesEnum<>((Class<E>) c));
  }

  public DirectBuffer byteRepr(final E value) {
    return valuesAsBuffers[value.ordinal()];
  }

  public E getValue(final DirectBuffer buffer) {
    final var value = parser.parse(buffer, 0, buffer.capacity());
    if (value == null) {
      throw new IllegalArgumentException(
          "No enum cases with string representation of " + BufferUtil.bufferAsString(buffer));
    }
    return value;
  }

  static <E extends Enum<E>> DirectBuffer[] byteRepresentations(final Class<E> enumClass) {
    return Arrays.stream(enumClass.getEnumConstants())
        .map(c -> c.toString().getBytes(StandardCharsets.UTF_8))
        .map(UnsafeBuffer::new)
        .toArray(DirectBuffer[]::new);
  }
}
