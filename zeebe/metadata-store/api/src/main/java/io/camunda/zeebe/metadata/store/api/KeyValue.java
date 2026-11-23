/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One key with its value, as {@link ClusterMetadataStore#get(StoreKey)} and {@link
 * ClusterMetadataStore#list(StoreKey)} return it. Values are opaque: the consumer serializes a
 * value before it writes it and deserializes it after a read. Equality compares the value bytes.
 *
 * @param key the key
 * @param current the current value
 * @param previous the value the last write replaced; absent when the key has none
 */
public record KeyValue(StoreKey key, byte[] current, byte @Nullable [] previous) {

  public KeyValue {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(current, "current");
  }

  @Override
  public boolean equals(final Object o) {
    return o
            instanceof
            KeyValue(final StoreKey otherKey, final byte[] otherCurrent, final byte[] otherPrevious)
        && key.equals(otherKey)
        && Arrays.equals(current, otherCurrent)
        && Arrays.equals(previous, otherPrevious);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, Arrays.hashCode(current), Arrays.hashCode(previous));
  }

  @Override
  public String toString() {
    return "KeyValue[key=%s, current=%s, previous=%s]"
        .formatted(key, Arrays.toString(current), Arrays.toString(previous));
  }
}
