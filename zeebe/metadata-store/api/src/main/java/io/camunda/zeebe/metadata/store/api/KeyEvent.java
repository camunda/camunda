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
 * changed, not the watched path. Which values are present follows from the type, and the
 * constructor enforces it.
 *
 * @param type what the change did to the key
 * @param key the key that changed
 * @param current the new value; absent for {@link ChangeType#REMOVE} only
 * @param previous the value this broker held before the change; absent for {@link ChangeType#ADD}
 *     only
 */
public record KeyEvent(
    ChangeType type, StoreKey key, @Nullable StoreValue current, @Nullable StoreValue previous) {

  /**
   * @throws IllegalArgumentException if the values present do not match the type
   */
  public KeyEvent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(key, "key");
    final boolean valid =
        switch (type) {
          case ADD -> current != null && previous == null;
          case REPLACE -> current != null && previous != null;
          case REMOVE -> current == null && previous != null;
        };
    if (!valid) {
      throw new IllegalArgumentException(
          "Expected the values of a %s event to match its type, but got current=%s, previous=%s"
              .formatted(type, current, previous));
    }
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
