/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.port.outbound;

import io.camunda.auth.domain.model.AuthUser;
import java.util.Optional;

/** Read-only port for user lookups. Always available regardless of persistence mode. */
public interface UserReadPort {
  Optional<AuthUser> findByUsername(String username);
  Optional<AuthUser> findByKey(long userKey);
}
