/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.camunda.zeebe.util.buffer.BufferReader;
import java.util.Optional;
import org.agrona.DirectBuffer;

/**
 * Reads the system partition's key-value store as replicated to this broker. Available on every
 * broker regardless of its role; reads may lag behind puts committed on other brokers.
 */
public interface SystemKeyValueReader {

  /** Reads the latest value of the key into the given value, if the key has one. Thread-safe. */
  <T extends BufferReader> Optional<T> get(SystemPartitionKeyType key, T value);

  /**
   * Notifies the listener every time a committed put changes the value of the key on this broker;
   * putting the value it already has does not notify. Watching with the same listener twice has no
   * effect. Thread-safe.
   */
  void watch(SystemPartitionKeyType key, ValueListener listener);

  /**
   * Stops notifying the listener of changes to the key; a listener that does not watch the key is
   * ignored. Thread-safe.
   */
  void unwatch(SystemPartitionKeyType key, ValueListener listener);

  /**
   * Notified of changes to the value of a watched key, on the system partition's actor thread, so
   * it must not block.
   */
  @FunctionalInterface
  interface ValueListener {

    /**
     * @param key the watched key
     * @param value the new value, which must not be modified
     */
    void onChange(SystemPartitionKeyType key, DirectBuffer value);
  }
}
