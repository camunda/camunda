/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static io.camunda.security.auth.Authorization.WILDCARD;

import java.util.Objects;

public record AuthorizationScope(MatcherType type, String resourceId) {

  @Override
  public boolean equals(final Object obj) {
    final AuthorizationScope other = (AuthorizationScope) obj;
    if (MatcherType.ANY == other.type) {
      return true;
    }
    return resourceId.equals(other.resourceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, resourceId);
  }

  public static class AuthorizationScopeFactory {

    public static AuthorizationScope wildcard() {
      return new AuthorizationScope(AuthorizationScope.MatcherType.ANY, null);
    }

    public static AuthorizationScope id(final String resourceId) {
      return new AuthorizationScope(AuthorizationScope.MatcherType.ID, resourceId);
    }

    public static AuthorizationScope of(final String resourceId) {
      if (WILDCARD.equals(resourceId)) {
        return wildcard();
      }

      return id(resourceId);
    }
  }

  public enum MatcherType {
    ANY,
    ID
  }
}
