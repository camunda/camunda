/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import org.jspecify.annotations.Nullable;

/**
 * The identity of a row in the write-behind store: its type and its primary key. Keys are ordered
 * by type, then by the key itself, which must be {@link Comparable}. The order only serves the
 * store's sorted maps; the relational state doesn't scan through the store.
 */
record EntityKey(EntityType<?, ?> type, Object key) implements Comparable<EntityKey> {

  /** Sorts before ({@code rank} -1) or after (1) the keys of a group, or of the type if null. */
  private record Bound(@Nullable Object group, int rank) {}

  /** Sorts before every key of the type, to start a range over the keys of the type. */
  static EntityKey first(final EntityType<?, ?> type) {
    return new EntityKey(type, new Bound(null, -1));
  }

  /** Sorts after every key of the type, to end a range over the keys of the type. */
  static EntityKey after(final EntityType<?, ?> type) {
    return new EntityKey(type, new Bound(null, 1));
  }

  /**
   * Sorts before every key of the group, to start a range over them. The type's keys must be
   * ordered by their group first.
   */
  static EntityKey first(final EntityType<?, ?> type, final Object group) {
    return new EntityKey(type, new Bound(group, -1));
  }

  /** Sorts after every key of the group, to end a range over them. */
  static EntityKey after(final EntityType<?, ?> type, final Object group) {
    return new EntityKey(type, new Bound(group, 1));
  }

  @Override
  @SuppressWarnings("unchecked")
  public int compareTo(final EntityKey other) {
    final int byType = Integer.compare(type.id, other.type.id);
    if (byType != 0) {
      return byType;
    }
    final Object otherKey = other.key;
    if (!(key instanceof Bound) && !(otherKey instanceof Bound)) {
      return ((Comparable<Object>) key).compareTo(otherKey);
    }
    if (key instanceof final Bound bound) {
      return compareBound(bound, otherKey);
    }
    return -compareBound((Bound) otherKey, key);
  }

  /** The order of a bound relative to a key or to another bound of the same type. */
  @SuppressWarnings("unchecked")
  private int compareBound(final Bound bound, final Object other) {
    final Object group = bound.group();
    final Object otherGroup;
    final int otherRank;
    if (other instanceof final Bound otherBound) {
      otherGroup = otherBound.group();
      otherRank = otherBound.rank();
    } else {
      otherGroup = type.groupOfKey(other);
      otherRank = 0;
    }
    if (group == null) {
      return otherGroup == null && otherRank != 0
          ? Integer.compare(bound.rank(), otherRank)
          : bound.rank();
    }
    if (otherGroup == null) {
      return otherRank == 0 ? 0 : -otherRank;
    }
    final int byGroup = ((Comparable<Object>) group).compareTo(otherGroup);
    return byGroup != 0 ? byGroup : Integer.compare(bound.rank(), otherRank);
  }
}
