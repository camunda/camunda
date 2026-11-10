/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import java.sql.SQLException;
import org.jspecify.annotations.Nullable;

/**
 * Identifies and loads the rows of one entity type for an {@link EntityCache}. Writing them is up
 * to the participant that drains the cache on commit, see {@link EntityCache#writeStaged}.
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
}
