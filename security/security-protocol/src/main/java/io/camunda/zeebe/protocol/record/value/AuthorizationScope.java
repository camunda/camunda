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

  public static final String WILDCARD_CHAR = "*";
  public static final AuthorizationScope WILDCARD =
      new AuthorizationScope(AuthorizationResourceMatcher.ANY, WILDCARD_CHAR);

  private AuthorizationResourceMatcher matcher;
  private String resourceId;
  private String resourcePropertyName;

  public AuthorizationScope() {}

  public AuthorizationScope(final AuthorizationResourceMatcher matcher, final String resourceId) {
    this(matcher, resourceId, "");
  }

  public AuthorizationScope(
      final AuthorizationResourceMatcher matcher,
      final String resourceId,
      final String resourcePropertyName) {
    this.matcher = matcher;
    this.resourceId = resourceId;

    // Since this class is used for comparisons, normalize null to empty string for this field
    // (introduced in 8.9.0) to handle cases where null is provided while empty string is expected.
    this.resourcePropertyName = resourcePropertyName != null ? resourcePropertyName : "";
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

  public String getResourcePropertyName() {
    return resourcePropertyName;
  }

  public AuthorizationScope setResourcePropertyName(final String resourcePropertyName) {
    this.resourcePropertyName = resourcePropertyName;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(matcher, resourceId, resourcePropertyName);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof AuthorizationScope) {
      final AuthorizationScope other = (AuthorizationScope) obj;
      return Objects.equals(matcher, other.matcher)
          && Objects.equals(resourceId, other.resourceId)
          && Objects.equals(resourcePropertyName, other.resourcePropertyName);
    }
    return false;
  }

  @Override
  public String toString() {
    return "AuthorizationScope{"
        + "matcher="
        + matcher
        + ", resourceId='"
        + resourceId
        + '\''
        + ", resourcePropertyName='"
        + resourcePropertyName
        + '\''
        + '}';
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

  public static AuthorizationScope property(final String resourcePropertyName)
      throws IllegalArgumentException {
    if (resourcePropertyName == null || resourcePropertyName.isEmpty()) {
      throw new IllegalArgumentException("Resource property name cannot be null or empty");
    }
    return new AuthorizationScope(AuthorizationResourceMatcher.PROPERTY, "", resourcePropertyName);
  }

  public static AuthorizationScope of(final String resourceId) {
    if (WILDCARD_CHAR.equals(resourceId)) {
      return WILDCARD;
    }

    return id(resourceId);
  }
}
