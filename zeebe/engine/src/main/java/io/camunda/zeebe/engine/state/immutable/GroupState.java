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
import java.util.function.BiFunction;

public interface GroupState {

  Optional<PersistedGroup> get(String groupId);

  /**
   * Loops over all groups and applies the provided callback. It stops looping over the groups when
   * the callback function returns false, otherwise it will continue until all groups are visited.
   */
  void forEachGroup(final BiFunction<String, PersistedGroup, Boolean> callback);

  /**
   * Finds a group by ID or name. First attempts to find by ID. If not found, searches all groups by
   * name.
   *
   * @param value the group ID or name to search for
   * @return list of matching groups (empty if none found, multiple if searching by name and
   *     multiple groups share the same name)
   */
  List<PersistedGroup> findByIdOrName(String value);
}
