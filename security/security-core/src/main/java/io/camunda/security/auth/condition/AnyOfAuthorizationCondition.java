/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth.condition;

import io.camunda.security.auth.Authorization;
import java.util.List;

/**
 * Disjunctive {@link AuthorizationCondition} that grants access when any child authorization is
 * satisfied.
 *
 * <p>This condition type is particularly useful when multiple authorization paths exist for
 * accessing a resource. For example, audit logs might be accessible through direct audit log
 * permissions, transitive process definition permissions, or user task permissions.
 *
 * <p>When combined with conditional authorizations, only applicable authorizations (those whose
 * conditions evaluate to {@code true} for a given document) are evaluated for access control.
 *
 * <p>Example:
 *
 * <pre>{@code
 * AuthorizationCondition condition = AuthorizationConditions.anyOf(
 *     directAuthorization,
 *     transitiveAuthorization.withCondition(predicate),
 *     anotherConditionalAuthorization
 * );
 * }</pre>
 *
 * @see io.camunda.security.auth.Authorization
 * @see io.camunda.security.auth.Authorization#appliesTo(Object)
 */
public record AnyOfAuthorizationCondition(List<Authorization<?>> authorizations)
    implements AuthorizationCondition {

  /**
   * @throws IllegalArgumentException when {@code authorizations} is {@code null} or empty
   */
  public AnyOfAuthorizationCondition {
    if (authorizations == null || authorizations.isEmpty()) {
      throw new IllegalArgumentException(
          "AnyOfAuthorizationCondition requires at least one authorization");
    }
    authorizations = List.copyOf(authorizations);
  }

  /**
   * Filters the authorizations to only those that are applicable to the given document.
   *
   * <p>This method evaluates each authorization's condition predicate (if present) to determine
   * which authorizations should be considered for access control. Only authorizations where {@link
   * Authorization#appliesTo(Object)} returns {@code true} are included in the result.
   *
   * <p>This filtering is crucial for implementing conditional authorization logic, where different
   * authorization rules may apply based on runtime properties of the resource being accessed.
   *
   * <p>Example: For an audit log with no process definition ID, process-based authorizations would
   * be filtered out, leaving only direct audit log permissions to be evaluated.
   *
   * @param document the document to test authorization applicability against
   * @param <T> the type of document
   * @return a list of authorizations whose conditions are satisfied for the given document
   * @see Authorization#appliesTo(Object)
   * @see Authorization#withCondition(java.util.function.Predicate)
   */
  public <T> List<Authorization<?>> applicableAuthorizations(final T document) {
    return authorizations().stream()
        .filter(
            auth -> {
              @SuppressWarnings("unchecked")
              final Authorization<T> typedAuth = (Authorization<T>) auth;
              return typedAuth.appliesTo(document);
            })
        .toList();
  }
}
