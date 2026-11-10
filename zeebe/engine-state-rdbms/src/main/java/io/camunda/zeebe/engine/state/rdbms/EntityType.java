/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import java.util.function.Function;

/**
 * A table whose rows an {@link EntitySession} stages, declared once as a constant. Its statements
 * are found by convention in the mapper's namespace: {@code select<Name>}, {@code insert<Name>},
 * {@code update<Name>} and {@code delete<Name>}. All of them take the {@code partitionId}; the
 * select and the delete the {@code key}, the insert and the update the {@code row}.
 *
 * <p>Rows must be immutable snapshots (e.g. records holding serialized payloads): the session hands
 * the same instance to every reader and to the flush. Keys must implement {@code equals} and {@code
 * hashCode}.
 *
 * @param <K> the primary key
 * @param <E> the row
 */
final class EntityType<K, E> {

  final String description;
  final String select;
  final String insert;
  final String update;
  final String delete;
  private final Function<E, K> keyOf;

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
    this.description = description;
    this.keyOf = keyOf;
    final var namespace = mapper.getName() + ".";
    select = namespace + "select" + name;
    insert = namespace + "insert" + name;
    update = namespace + "update" + name;
    delete = namespace + "delete" + name;
  }

  K keyOf(final E row) {
    return keyOf.apply(row);
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
