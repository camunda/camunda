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

public record UserFilter(
    Long key, String username, Set<String> usernames, String name, String email, String tenantId)
    implements FilterBase {

  public Builder toBuilder() {
    return new Builder()
        .username(username)
        .usernames(usernames)
        .name(name)
        .email(email)
        .tenantId(tenantId);
  }

  public static final class Builder implements ObjectBuilder<UserFilter> {
    private Long key;
    private String username;
    private Set<String> usernames;
    private String name;
    private String email;
    private String tenantId;

    public Builder key(final Long value) {
      key = value;
      return this;
    }

    public Builder username(final String value) {
      username = value;
      return this;
    }

    public Builder usernames(final Set<String> value) {
      usernames = value == null ? Set.of() : value;
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

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    @Override
    public UserFilter build() {
      return new UserFilter(key, username, usernames, name, email, tenantId);
    }
  }
}
