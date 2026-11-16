/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.NavigableMap;
import org.jspecify.annotations.Nullable;

/**
 * The unflushed versions of a range of keys as of one commit, read from the batches themselves
 * instead of from a copy of them: opening a scan costs nothing, and a scan only pays for the
 * entries that it visits.
 *
 * <p>The sealed batch no longer changes. The active batch does: commits after {@code viewSeq} add
 * versions to it, and {@link Version#asOf} steps past them to the version that the view needs. A
 * key that only later commits wrote is not part of the view. When the writer seals the active batch
 * and flushes it, the batch stays here, complete, while the storage cursor of the same view still
 * reads the state from before the flush.
 *
 * <p>The navigation methods follow {@link NavigableMap}. On equal keys the active batch wins.
 */
final class DirtyRange<K extends Comparable<K>, V> {

  private final NavigableMap<K, Version<V>> active;
  private final @Nullable NavigableMap<K, Version<V>> sealed;
  private final long viewSeq;

  /**
   * @param active the active batch, restricted to the range of the view
   * @param sealed the sealed batch, restricted to the range of the view, if there is one
   * @param viewSeq the sequence of the last commit that the view includes
   */
  DirtyRange(
      final NavigableMap<K, Version<V>> active,
      final @Nullable NavigableMap<K, Version<V>> sealed,
      final long viewSeq) {
    this.active = active;
    this.sealed = sealed;
    this.viewSeq = viewSeq;
  }

  /** The first entry at or after the key. */
  @Nullable Entry<K, Version<V>> ceilingEntry(final K key) {
    return nearest(key, Step.CEILING);
  }

  /** The first entry after the key. */
  @Nullable Entry<K, Version<V>> higherEntry(final K key) {
    return nearest(key, Step.HIGHER);
  }

  /** The last entry at or before the key. */
  @Nullable Entry<K, Version<V>> floorEntry(final K key) {
    return nearest(key, Step.FLOOR);
  }

  /** The last entry before the key. */
  @Nullable Entry<K, Version<V>> lowerEntry(final K key) {
    return nearest(key, Step.LOWER);
  }

  private @Nullable Entry<K, Version<V>> nearest(final K key, final Step step) {
    final var fromActive = visible(active, key, step);
    final var fromSealed = sealed == null ? null : visible(sealed, key, step);
    if (fromActive == null) {
      return fromSealed;
    }
    if (fromSealed == null) {
      return fromActive;
    }
    final int comparison = fromActive.getKey().compareTo(fromSealed.getKey());
    if (comparison == 0) {
      return fromActive;
    }
    return (step.forward == comparison < 0) ? fromActive : fromSealed;
  }

  /** The nearest entry that has a version as of the view, with that version. */
  private @Nullable Entry<K, Version<V>> visible(
      final NavigableMap<K, Version<V>> batch, final K key, final Step step) {
    var entry = step.apply(batch, key);
    while (entry != null) {
      final var version = entry.getValue().asOf(viewSeq);
      if (version != null) {
        return new SimpleImmutableEntry<>(entry.getKey(), version);
      }
      entry = (step.forward ? Step.HIGHER : Step.LOWER).apply(batch, entry.getKey());
    }
    return null;
  }

  private enum Step {
    CEILING(true),
    HIGHER(true),
    FLOOR(false),
    LOWER(false);

    private final boolean forward;

    Step(final boolean forward) {
      this.forward = forward;
    }

    <K, V> @Nullable Entry<K, V> apply(final NavigableMap<K, V> map, final K key) {
      return switch (this) {
        case CEILING -> map.ceilingEntry(key);
        case HIGHER -> map.higherEntry(key);
        case FLOOR -> map.floorEntry(key);
        case LOWER -> map.lowerEntry(key);
      };
    }
  }
}
