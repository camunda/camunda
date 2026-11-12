/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A table whose rows an {@link EntitySession} stages, declared once as a constant. Its statements
 * are found by convention in the mapper's namespace: {@code select<Name>}, {@code insert<Name>},
 * {@code update<Name>} and {@code delete<Name>}. All of them take the {@code partitionId}; the
 * select and the delete the {@code key}, the insert and the update the {@code row}.
 *
 * <p>A type may group its rows, e.g. the variables by scope: the group of a key is derived from the
 * key. The session can then load all rows of a group at once and remember which groups it loaded
 * completely, see {@link EntitySession#queryGroups}.
 *
 * <p>Rows must be immutable snapshots (e.g. records holding serialized payloads): the session hands
 * the same instance to every reader and to the flush. Keys must implement {@code equals} and {@code
 * hashCode}, and must be {@link Comparable} if the type is written through a write-behind store.
 *
 * @param <K> the primary key
 * @param <E> the row
 */
final class EntityType<K, E> {

  private static final AtomicInteger IDS = new AtomicInteger();

  /** Tells the types apart in an ordering, and names the type's group of rows. */
  final int id = IDS.incrementAndGet();

  final String description;
  final String select;
  final String insert;
  final String update;
  final String delete;
  private final Function<E, K> keyOf;
  private final @Nullable Function<K, ?> groupOf;

  /**
   * @param mapper the mapper whose namespace holds the statements
   * @param name the suffix of the statement ids, e.g. {@code ElementInstance}
   * @param description how error messages name a row, e.g. {@code element instance}
   * @param keyOf the primary key of a row
   */
  EntityType(
      final Class<?> mapper,
      final String name,
      final String description,
      final Function<E, K> keyOf) {
    this(mapper, name, description, keyOf, null);
  }

  /**
   * Like {@link #EntityType(Class, String, String, Function)}, for a type whose rows are grouped.
   *
   * @param groupOf the group of a key; groups must implement {@code equals} and {@code hashCode}
   */
  EntityType(
      final Class<?> mapper,
      final String name,
      final String description,
      final Function<E, K> keyOf,
      final @Nullable Function<K, ?> groupOf) {
    this.description = description;
    this.keyOf = keyOf;
    this.groupOf = groupOf;
    final var namespace = mapper.getName() + ".";
    select = namespace + "select" + name;
    insert = namespace + "insert" + name;
    update = namespace + "update" + name;
    delete = namespace + "delete" + name;
  }

  K keyOf(final E row) {
    return keyOf.apply(row);
  }

  /**
   * @return the group of the key, or null if the type doesn't group its rows
   */
  @Nullable Object groupOf(final K key) {
    return groupOf == null ? null : groupOf.apply(key);
  }

  /** Like {@link #groupOf}, for a key that the session holds untyped. */
  @Nullable Object groupOfKey(final Object key) {
    return groupOf(castKey(key));
  }

  /**
   * The engine key a row belongs to: its key, or else its group, if that is a {@code long}, e.g.
   * the element instance of an event scope or the scope of a variable; otherwise -1. While that key
   * is new in a transaction, the database has no row of it, see {@link
   * EntitySession#isGeneratedInTransaction}.
   */
  long engineKeyOf(final K key) {
    if (key instanceof final Long engineKey) {
      return engineKey;
    }
    return groupOf(key) instanceof final Long engineKey ? engineKey : -1L;
  }

  /** A key of this type, as staged by the session. */
  @SuppressWarnings("unchecked")
  K castKey(final Object key) {
    return (K) key;
  }

  /** A row of this type, as staged by the session. */
  @SuppressWarnings("unchecked")
  E cast(final Object row) {
    return (E) row;
  }

  @Override
  public String toString() {
    return description;
  }
}
