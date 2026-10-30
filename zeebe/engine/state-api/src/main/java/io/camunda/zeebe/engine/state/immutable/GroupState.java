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

  Optional<PersistedGroup> get(String groupId);

  /**
   * Finds groups by ID or name. First attempts to resolve {@code value} as a group ID. If no group
   * with that ID exists, all groups are searched by name.
   *
   * @param value the group ID or name to search for
   * @return list of matching groups: empty if none found, a single group when matched by ID or a
   *     unique name, or multiple groups when several groups share the same name
   */
  List<PersistedGroup> findByIdOrName(String value);
}
