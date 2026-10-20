/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.group.PersistedGroup;
import java.util.List;
import java.util.Optional;

public interface GroupState {

  /** Returns the group visible to a command outside of a policy update. */
  default Optional<PersistedGroup> get(final String groupId) {
    return get(groupId, 0);
  }

  /**
   * Returns the group visible to a command of the given policy version. The command sees the
   * current version and its own version, so a policy update reads its own writes.
   */
  Optional<PersistedGroup> get(String groupId, long commandVersion);

  /**
   * Finds groups by ID or name. First attempts to resolve {@code value} as a group ID. If no group
   * with that ID exists, all groups are searched by name.
   *
   * @param value the group ID or name to search for
   * @return list of matching groups: empty if none found, a single group when matched by ID or a
   *     unique name, or multiple groups when several groups share the same name
   */
  default List<PersistedGroup> findByIdOrName(final String value) {
    return findByIdOrName(value, 0);
  }

  /** Like {@link #findByIdOrName(String)}, but reads the groups visible to the command version. */
  List<PersistedGroup> findByIdOrName(String value, long commandVersion);
}
