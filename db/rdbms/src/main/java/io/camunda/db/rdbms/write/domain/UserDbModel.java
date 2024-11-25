/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record UserDbModel(Long userKey, String username, String name, String email)
    implements DbModel<UserDbModel> {

  @Override
  public UserDbModel copy(
      final Function<ObjectBuilder<UserDbModel>, ObjectBuilder<UserDbModel>> copyFunction) {
    return copyFunction.apply(new Builder().username(username).name(name).email(email)).build();
  }

  public static class Builder implements ObjectBuilder<UserDbModel> {

    private Long userKey;
    private String username;
    private String name;
    private String email;

    public Builder() {}

    // Builder methods for each field
    public Builder userKey(final Long userKey) {
      this.userKey = userKey;
      return this;
    }

    public Builder username(final String username) {
      this.username = username;
      return this;
    }

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder email(final String email) {
      this.email = email;
      return this;
    }

    // Build method to create the record
    @Override
    public UserDbModel build() {
      return new UserDbModel(userKey, username, name, email);
    }
  }
}
