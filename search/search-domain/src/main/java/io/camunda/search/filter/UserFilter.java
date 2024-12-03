/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;
import java.util.Set;

public record UserFilter(Set<Long> keys, String username, String name, String email, Long roleKey)
    implements FilterBase {
  public Builder toBuilder() {
    return new Builder().keys(keys).username(username).name(name).email(email).roleKey(roleKey);
  }

  public static final class Builder implements ObjectBuilder<UserFilter> {
    private Set<Long> keys;
    private String username;
    private String name;
    private String email;
    private Long roleKey;

    public Builder keys(final Set<Long> value) {
      keys = value;
      return this;
    }

    public Builder key(final Long value) {
      keys = value == null ? Set.of() : Set.of(value);
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

    public Builder roleKey(final Long value) {
      roleKey = value;
      return this;
    }

    @Override
    public UserFilter build() {
      return new UserFilter(keys, username, name, email, roleKey);
    }
  }
}
