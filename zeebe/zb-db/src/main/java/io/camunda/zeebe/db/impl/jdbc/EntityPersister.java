/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Reads and writes the rows of one entity type for an {@link EntityCache}.
 *
 * <p>Entities must be immutable snapshots (e.g. records holding serialized payloads): the cache
 * hands the same instance to every reader and to the flush, so a mutable entity would let a caller
 * change staged state behind the cache's back. Keys must implement {@code equals} and {@code
 * hashCode}.
 *
 * @param <K> the entity's primary key
 * @param <E> the entity
 */
public interface EntityPersister<K, E> {

  /** The primary key of the given entity. */
  K keyOf(E entity);

  /** Loads the committed entity with the given key, or returns null if there is none. */
  @Nullable E load(K key) throws SQLException;

  /**
   * Writes the given changes on the given connection, without committing. Every key appears in at
   * most one of the lists, so the order of the statements doesn't matter.
   *
   * @return the number of rows each update affected, in the order of {@code updates}; {@link
   *     java.sql.Statement#SUCCESS_NO_INFO} if the driver doesn't know
   */
  int[] flush(Connection connection, List<E> inserts, List<E> updates, List<K> deletes)
      throws SQLException;
}
