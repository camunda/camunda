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
 * One change of one key, as {@link ClusterMetadataStore#watch} delivers it. The key is the key that
 * changed, not the watched path. Equality compares the value bytes.
 *
 * @param type what the change did to the key
 * @param key the key that changed
 * @param current the new value; absent for {@link ChangeType#REMOVE}
 * @param previous the value this broker held before the change; absent for {@link ChangeType#ADD}
 */
public record KeyEvent(
    ChangeType type, StoreKey key, byte @Nullable [] current, byte @Nullable [] previous) {

  public KeyEvent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(key, "key");
  }

  @Override
  public boolean equals(final Object o) {
    return o
            instanceof
            KeyEvent(
                final ChangeType otherType,
                final StoreKey otherKey,
                final byte[] otherCurrent,
                final byte[] otherPrevious)
        && type == otherType
        && key.equals(otherKey)
        && Arrays.equals(current, otherCurrent)
        && Arrays.equals(previous, otherPrevious);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, key, Arrays.hashCode(current), Arrays.hashCode(previous));
  }

  @Override
  public String toString() {
    return "KeyEvent[type=%s, key=%s, current=%s, previous=%s]"
        .formatted(type, key, Arrays.toString(current), Arrays.toString(previous));
  }

  /**
   * What a change did to a key, as this broker saw it. New kinds may be added; a consumer that
   * switches over them keeps a default branch.
   */
  public enum ChangeType {
    /** The key held no value before: current is set, previous is absent. */
    ADD,
    /** The key held a value and holds another one now: current and previous are set. */
    REPLACE,
    /** The key was deleted: current is absent, previous is the removed value. */
    REMOVE
  }
}
