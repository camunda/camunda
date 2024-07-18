/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import io.camunda.util.ObjectBuilder;

public record AuthorizationFilter(
    Long authorizationKey, String username, String resourceType, String resourceKey)
    implements FilterBase {
  public static final class Builder implements ObjectBuilder<AuthorizationFilter> {
    private Long authorizationKey;
    private String username;
    private String resourceType;
    private String resourceKey;

    public Builder authorizationKey(final Long value) {
      authorizationKey = value;
      return this;
    }

    public Builder username(final String value) {
      username = value;
      return this;
    }

    public Builder resourceType(final String value) {
      resourceType = value;
      return this;
    }

    public Builder resourceKey(final String value) {
      resourceKey = value;
      return this;
    }

    @Override
    public AuthorizationFilter build() {
      return new AuthorizationFilter(authorizationKey, username, resourceType, resourceKey);
    }
  }
}
