/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import io.camunda.util.ObjectBuilder;

public record UserFilter(Long key, String username, String name, String email)
    implements FilterBase {
  public static final class Builder implements ObjectBuilder<UserFilter> {
    private Long key;
    private String username;
    private String name;
    private String email;

    public Builder key(final Long value) {
      key = value;
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
      return new UserFilter(key, username, name, email);
    }
  }
}
