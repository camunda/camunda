/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

import java.util.Objects;

public class AuthorizationScope {

  private static final String WILDCARD_CHAR = "*";
  public static final AuthorizationScope WILDCARD =
      new AuthorizationScope(AuthorizationResourceMatcher.ANY, WILDCARD_CHAR);

  private AuthorizationResourceMatcher matcher;
  private String resourceId;

  public AuthorizationScope() {}

  public AuthorizationScope(final AuthorizationResourceMatcher matcher, final String resourceId) {
    this.matcher = matcher;
    this.resourceId = resourceId;
  }

  public AuthorizationResourceMatcher getMatcher() {
    return matcher;
  }

  public AuthorizationScope setMatcher(final AuthorizationResourceMatcher matcher) {
    this.matcher = matcher;
    return this;
  }

  public String getResourceId() {
    return resourceId;
  }

  public AuthorizationScope setResourceId(final String resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(matcher, resourceId);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof AuthorizationScope) {
      final AuthorizationScope other = (AuthorizationScope) obj;
      return matcher.equals(other.matcher) && resourceId.equals(other.resourceId);
    }
    return false;
  }

  public static AuthorizationScope id(final String resourceId) throws IllegalArgumentException {
    if (WILDCARD_CHAR.equals(resourceId)) {
      final String errorMsg =
          String.format(
              "Resource ID cannot be the wildcard character '%s'. For declaring WILDCARD access, please use the AuthorizationScope.WILDCARD constant.",
              WILDCARD_CHAR);
      throw new IllegalArgumentException(errorMsg);
    }
    return new AuthorizationScope(AuthorizationResourceMatcher.ID, resourceId);
  }

  public static AuthorizationScope of(final String resourceId) {
    if (WILDCARD_CHAR.equals(resourceId)) {
      return WILDCARD;
    }

    return id(resourceId);
  }
}
