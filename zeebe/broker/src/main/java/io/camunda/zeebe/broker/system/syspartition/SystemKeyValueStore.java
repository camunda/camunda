/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * In-memory key-value store holding the system partition's state: the latest value put under every
 * key. It is changed only by applying committed log entries, so every broker converges on the same
 * content. It is made durable by the Raft log and the JSON snapshots, not by a database of its own.
 */
final class SystemKeyValueStore implements SystemKeyValueReader {
  private final Map<Long, byte[]> entries = new ConcurrentHashMap<>();
  private final Map<SystemPartitionKeyType, Set<ValueListener>> listeners =
      new ConcurrentHashMap<>();
  private final SystemPartitionEntry entry = new SystemPartitionEntry();

  @Override
  public <T extends BufferReader> Optional<T> get(final SystemPartitionKeyType key, final T value) {
    final var bytes = entries.get(key.id());
    if (bytes == null) {
      return Optional.empty();
    }
    value.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    return Optional.of(value);
  }

  @Override
  public void watch(final SystemPartitionKeyType key, final ValueListener listener) {
    listeners.computeIfAbsent(key, ignored -> new CopyOnWriteArraySet<>()).add(listener);
  }

  @Override
  public void unwatch(final SystemPartitionKeyType key, final ValueListener listener) {
    listeners.getOrDefault(key, Set.of()).remove(listener);
  }

  void apply(final DirectBuffer data) {
    entry.wrap(data, 0, data.capacity());
    final var value = BufferUtil.bufferAsArray(entry.getValue());
    final var previous = entries.put(entry.getKey(), value);
    if (!Arrays.equals(previous, value)) {
      SystemPartitionKeyType.fromId(entry.getKey()).ifPresent(key -> notify(key, value));
    }
  }

  private void notify(final SystemPartitionKeyType key, final byte[] value) {
    final var view = new UnsafeBuffer(value);
    listeners.getOrDefault(key, Set.of()).forEach(listener -> listener.onChange(key, view));
  }

  Map<Long, byte[]> entries() {
    return Map.copyOf(entries);
  }

  void restore(final Map<Long, byte[]> restored) {
    entries.clear();
    entries.putAll(restored);
  }
}
