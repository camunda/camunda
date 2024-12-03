/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;
import java.util.HashSet;
import java.util.Set;

public record UserFilter(Set<Long> keys, String username, String name, String email)
    implements FilterBase {
  public static final class Builder implements ObjectBuilder<UserFilter> {
    private Set<Long> keys = new HashSet<>();
    private String username;
    private String name;
    private String email;

    @Deprecated
    public Builder key(final Long value) {
      if (value != null) {
        keys = Set.of(value);
      } else {
        keys = new HashSet<>();
      }
      return this;
    }

    public Builder keys(final Set<Long> values) {
      keys = values;
      return this;
    }

    public Builder username(final String value) {
      username = value;
      return this;
    }

    public Builder name(final String value) {
      name = value;
      return this;
    }

    public Builder email(final String value) {
      email = value;
      return this;
    }

    @Override
    public UserFilter build() {
      return new UserFilter(keys, username, name, email);
    }
  }
}
