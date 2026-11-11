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

  @Override
  @SuppressWarnings("unchecked")
  public int compareTo(final EntityKey other) {
    final int byType = Integer.compare(type.id, other.type.id);
    return byType != 0 ? byType : ((Comparable<Object>) key).compareTo(other.key);
  }
}
