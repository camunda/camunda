/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth.condition;

import io.camunda.security.core.auth.RequiredAuthorization;
import java.util.List;

/**
 * Describes how a {@code SecurityContext} should evaluate authorizations when securing a query.
 * Implementations can wrap a single {@link RequiredAuthorization} or compose multiple
 * authorizations (for example, disjunctive {@code anyOf} checks). Search backends inspect the
 * concrete condition type to translate it into backend specific predicates while callers express
 * their intent declaratively.
 */
public interface AuthorizationCondition {

  /** Returns the underlying authorizations (single returns a size==1 list). */
  default List<RequiredAuthorization<?>> authorizations() {

    if (this instanceof SingleAuthorizationCondition(RequiredAuthorization<?> authorization)) {
      return List.of(authorization);
    }

    if (this instanceof AnyOfAuthorizationCondition(List<RequiredAuthorization<?>> children)) {
      return children;
    }

    throw new IllegalStateException("Unknown AuthorizationCondition type: " + getClass());
  }
}
