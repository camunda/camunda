/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.condition.AuthorizationCondition;
import io.camunda.security.auth.condition.AuthorizationConditions;

/**
 * Enables or disables a {@link AuthorizationCheck}. If enabled, then the {@code
 * authorizationCondition} to be checked must be provided.
 */
public record AuthorizationCheck(boolean enabled, AuthorizationCondition authorizationCondition) {

  public static AuthorizationCheck enabled(final Authorization<?> authorization) {
    return enabled(AuthorizationConditions.single(authorization));
  }

  public static AuthorizationCheck enabled(final AuthorizationCondition authorizationCondition) {
    return new AuthorizationCheck(true, authorizationCondition);
  }

  public static AuthorizationCheck disabled() {
    return new AuthorizationCheck(false, null);
  }

  public boolean hasAnyResourceAccess() {
    return !enabled || hasAnyResourceIdAccess();
  }

  private boolean hasAnyResourceIdAccess() {
    if (authorizationCondition == null) {
      return false;
    }

    final var auths = authorizationCondition.authorizations();
    if (auths == null || auths.isEmpty()) {
      return false;
    }

    return auths.stream().anyMatch(Authorization::hasAnyResourceIds);
  }
}
