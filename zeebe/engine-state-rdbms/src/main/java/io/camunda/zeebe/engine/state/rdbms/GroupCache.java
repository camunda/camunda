/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The committed rows of whole groups of an {@link EntityType}, e.g. the variables of a scope, kept
 * across transactions. A group is selected from the database by the value that it is grouped by,
 * not by a key range, so the cache is a map from the group to its rows instead of a window of keys.
 *
 * <p>A group stays valid for as long as it is cached: the partition's one writer changes rows only
 * through {@link EntitySession#commit}, which applies every written row to the cached group it
 * belongs to. The least recently used groups are evicted, and a group with more rows than {@link
 * #MAX_ROWS_PER_GROUP} is not cached.
 *
 * <p>The rows are immutable and shared with the readers. The cache is used by the partition's
 * actor, but synchronized to be safe for the sessions of the contexts that it hands out.
 */
final class GroupCache {

  static final int MAX_ROWS_PER_GROUP = 1_000;

  private final Map<GroupId, Map<Object, Object>> groups;

  /**
   * @param maxGroups how many groups the cache holds
   */
  GroupCache(final int maxGroups) {
    groups =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(
              final Map.Entry<GroupId, Map<Object, Object>> eldest) {
            return size() > maxGroups;
          }
        };
  }

  /** The rows of the group by key, or null if the group isn't cached. Don't change the map. */
  synchronized @Nullable Map<Object, Object> get(final EntityType<?, ?> type, final Object group) {
    return groups.get(new GroupId(type, group));
  }

  /** Caches the committed rows of a group, unless it is too big. */
  synchronized void put(
      final EntityType<?, ?> type, final Object group, final Map<Object, Object> rows) {
    if (rows.size() <= MAX_ROWS_PER_GROUP) {
      groups.put(new GroupId(type, group), new HashMap<>(rows));
    }
  }

  /** Applies a committed write to its group, if the group is cached; a null row is a delete. */
  synchronized void apply(
      final EntityType<?, ?> type, final Object key, final @Nullable Object row) {
    final Object group = groupOf(type, key);
    if (group == null) {
      return;
    }
    final var id = new GroupId(type, group);
    final var rows = groups.get(id);
    if (rows == null) {
      return;
    }
    if (row == null) {
      rows.remove(key);
    } else if (rows.size() < MAX_ROWS_PER_GROUP || rows.containsKey(key)) {
      rows.put(key, row);
    } else {
      // grew too big
      groups.remove(id);
    }
  }

  @SuppressWarnings("unchecked")
  private static @Nullable Object groupOf(final EntityType<?, ?> type, final Object key) {
    return ((EntityType<Object, ?>) type).groupOf(key);
  }

  private record GroupId(EntityType<?, ?> type, Object group) {}
}
