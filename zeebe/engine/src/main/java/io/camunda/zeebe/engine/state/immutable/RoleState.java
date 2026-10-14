/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.authorization.PersistedRole;
import java.util.Optional;

public interface RoleState {

  /** Returns the role visible to a command outside of a policy update. */
  default Optional<PersistedRole> getRole(final String roleId) {
    return getRole(roleId, 0);
  }

  /**
   * Returns the role visible to a command of the given policy version. The command sees the current
   * version and its own version, so a policy update reads its own writes. A command outside of a
   * policy update passes version 0.
   */
  Optional<PersistedRole> getRole(String roleId, long policyVersion);
}
