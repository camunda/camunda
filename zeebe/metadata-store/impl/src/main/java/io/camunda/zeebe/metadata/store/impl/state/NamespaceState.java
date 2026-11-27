/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.state;

import io.camunda.zeebe.metadata.store.api.KeyValue;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * One namespace: its pairs by key, in key order, and its metadata. Never changed once published.
 *
 * @param values the pairs by canonical key, in natural string order
 * @param version the Camunda version of the writer whose entry last changed the namespace
 * @param incarnation the position of the entry that created the namespace
 * @param lastUpdatedTimestamp the writer's clock in the last entry that changed the namespace
 * @param lastUpdatedPosition the position of the last entry that changed the namespace
 */
public record NamespaceState(
    NavigableMap<String, KeyValue> values,
    String version,
    long incarnation,
    long lastUpdatedTimestamp,
    long lastUpdatedPosition) {

  /** The values are copied, so a caller that changes its map later cannot change this state. */
  public NamespaceState {
    Objects.requireNonNull(version, "version");
    values = Collections.unmodifiableNavigableMap(new TreeMap<>(values));
  }

  /** Returns the pair stored under exactly this key. */
  public @Nullable KeyValue get(final String key) {
    return values.get(key);
  }

  /** Returns the pairs at or under the prefix, in key order; the empty prefix returns all. */
  public SequencedMap<String, KeyValue> atOrUnder(final String prefix) {
    return KeyPath.atOrUnder(values, prefix);
  }
}
