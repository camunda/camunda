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

  public KeyEvent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(key, "key");
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
