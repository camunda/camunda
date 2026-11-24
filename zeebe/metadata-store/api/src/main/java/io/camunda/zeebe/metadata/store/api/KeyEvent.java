/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One change of one key, as {@link ClusterMetadataStore#watch} delivers it. The key is the key that
 * changed, not the watched path.
 *
 * @param type what the change did to the key
 * @param key the key that changed
 * @param current the new value; absent for {@link ChangeType#REMOVE}
 * @param previous the value this broker held before the change; absent for {@link ChangeType#ADD}
 */
public record KeyEvent(
    ChangeType type, StoreKey key, @Nullable Value current, @Nullable Value previous) {

  /**
   * @throws IllegalArgumentException if current or previous is set or absent against the rule of
   *     the change type
   */
  public KeyEvent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(key, "key");
    final boolean expectsCurrent = type != ChangeType.REMOVE;
    final boolean expectsPrevious = type != ChangeType.ADD;
    if ((current != null) != expectsCurrent || (previous != null) != expectsPrevious) {
      throw new IllegalArgumentException(
          "Expected a %s event to have current %s and previous %s, but got current=%s, previous=%s"
              .formatted(
                  type,
                  expectsCurrent ? "set" : "absent",
                  expectsPrevious ? "set" : "absent",
                  current,
                  previous));
    }
  }

  public static KeyEvent added(final StoreKey key, final Value current) {
    return new KeyEvent(ChangeType.ADD, key, Objects.requireNonNull(current, "current"), null);
  }

  public static KeyEvent replaced(final StoreKey key, final Value current, final Value previous) {
    return new KeyEvent(
        ChangeType.REPLACE,
        key,
        Objects.requireNonNull(current, "current"),
        Objects.requireNonNull(previous, "previous"));
  }

  public static KeyEvent removed(final StoreKey key, final Value previous) {
    return new KeyEvent(ChangeType.REMOVE, key, null, Objects.requireNonNull(previous, "previous"));
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
