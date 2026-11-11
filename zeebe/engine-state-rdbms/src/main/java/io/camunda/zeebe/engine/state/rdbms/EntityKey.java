/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

/**
 * The identity of a row in the write-behind store: its type and its primary key. Keys are ordered
 * by type, then by the key itself, which must be {@link Comparable}. The order only serves the
 * store's sorted maps; the relational state doesn't scan through the store.
 */
record EntityKey(EntityType<?, ?> type, Object key) implements Comparable<EntityKey> {

  private static final Object BEFORE_ALL = new Object();
  private static final Object AFTER_ALL = new Object();

  /** Sorts before every key of the type, to start a range over the keys of the type. */
  static EntityKey first(final EntityType<?, ?> type) {
    return new EntityKey(type, BEFORE_ALL);
  }

  /** Sorts after every key of the type, to end a range over the keys of the type. */
  static EntityKey after(final EntityType<?, ?> type) {
    return new EntityKey(type, AFTER_ALL);
  }

  @Override
  @SuppressWarnings("unchecked")
  public int compareTo(final EntityKey other) {
    final int byType = Integer.compare(type.id, other.type.id);
    if (byType != 0) {
      return byType;
    }
    final int byRank = Integer.compare(rank(key), rank(other.key));
    if (byRank != 0 || rank(key) != 0) {
      return byRank;
    }
    return ((Comparable<Object>) key).compareTo(other.key);
  }

  private static int rank(final Object key) {
    return key == BEFORE_ALL ? -1 : key == AFTER_ALL ? 1 : 0;
  }
}
