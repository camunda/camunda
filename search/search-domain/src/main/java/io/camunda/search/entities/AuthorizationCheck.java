/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import io.camunda.security.auth.Authorization;
import java.util.function.Function;

public record AuthorizationCheck<T>(Function<T, String> fn) {

  public static <T> AuthorizationCheck<T> of(final Function<Builder<T>, Builder<T> fn) {
    return fn.apply(new Builder<>()).build();
  }

  public static class Builder<T> {
    private String foo;
    private Function<T, String> fn;

    public Builder<AuthorizationEntity> authorization() {
      foo = "HELLO";
      return (Builder<AuthorizationEntity>) this;
    }

    public Builder<T> bar(final Function<T, String> authorization) {
      fn = authorization;
      return this;
    }

    public Builder<T> foo() {
      foo = "abc";
      return this;
    }

    public AuthorizationCheck<T> build() {
      return new AuthorizationCheck<>(fn);
    }
  }
}
