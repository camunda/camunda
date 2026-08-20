/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.user.PersistedUser;
import java.util.Optional;

public interface UserState {

  Optional<PersistedUser> getUser(final String username);

  /**
   * Returns a user by its key. If no user was found, an empty optional is returned.
   *
   * @param userKey the key of the user
   * @return An optional containing the user if it was found, otherwise an empty optional
   */
  Optional<PersistedUser> getUser(final long userKey);
}
