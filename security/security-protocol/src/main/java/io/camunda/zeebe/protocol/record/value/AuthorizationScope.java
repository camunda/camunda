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
      new AuthorizationScope(AuthorizationResourceMatcher.ANY, WILDCARD_CHAR, null);

  private AuthorizationResourceMatcher matcher;
  private String resourceId;
  private String propertyName;

  public AuthorizationScope() {}

  public AuthorizationScope(
      final AuthorizationResourceMatcher matcher,
      final String resourceId,
      final String propertyName) {
    this.matcher = matcher;
    this.resourceId = resourceId;
    this.propertyName = propertyName;
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

  public String getPropertyName() {
    return propertyName;
  }

  public void setPropertyName(final String propertyName) {
    this.propertyName = propertyName;
  }

  @Override
  public int hashCode() {
    return Objects.hash(matcher, resourceId, propertyName);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof AuthorizationScope) {
      final AuthorizationScope other = (AuthorizationScope) obj;
      return matcher.equals(other.matcher)
          && resourceId.equals(other.resourceId)
          && propertyName.equals(other.propertyName);
    }
    return false;
  }

  public static AuthorizationScope propertyName(final String propertyName)
      throws IllegalArgumentException {
    return new AuthorizationScope(AuthorizationResourceMatcher.PROPERTY, null, propertyName);
  }

  public static AuthorizationScope id(final String resourceId) throws IllegalArgumentException {
    if (WILDCARD_CHAR.equals(resourceId)) {
      final String errorMsg =
          String.format(
              "Resource ID cannot be the wildcard character '%s'. For declaring WILDCARD access, please use the AuthorizationScope.WILDCARD constant.",
              WILDCARD_CHAR);
      throw new IllegalArgumentException(errorMsg);
    }
    return new AuthorizationScope(AuthorizationResourceMatcher.ID, resourceId, null);
  }

  public static AuthorizationScope of(final String resourceId) {
    if (WILDCARD_CHAR.equals(resourceId)) {
      return WILDCARD;
    }

    return id(resourceId);
  }
}
