/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth.condition;

import io.camunda.security.auth.Authorization;
import java.util.Arrays;
import java.util.List;

/**
 * Factory helpers for building {@link AuthorizationCondition} instances when constructing a {@code
 * SecurityContext}.
 */
public final class AuthorizationConditions {

  private AuthorizationConditions() {
    // utility class
  }

  /** Wraps a single authorization as an authorization condition. */
  public static AuthorizationCondition single(final Authorization<?> authorization) {
    return new SingleAuthorizationCondition(authorization);
  }

  /** Combines multiple authorizations as a disjunctive authorization condition. */
  public static AuthorizationCondition anyOf(final List<Authorization<?>> authorizations) {
    return new AnyOfAuthorizationCondition(authorizations);
  }

  /** Combines multiple authorizations as a disjunctive authorization condition. */
  public static AuthorizationCondition anyOf(final Authorization<?>... authorizations) {
    return anyOf(Arrays.asList(authorizations));
  }
}
