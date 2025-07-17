/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.security.auth.Authorization.WILDCARD;
import static io.camunda.security.auth.Authorization.withResourceIds;

import io.camunda.security.auth.SecurityContext;
import io.camunda.security.impl.AuthorizationChecker;

/**
 * Document based datastore (ES/OS) strategy implementation of {@link AuthorizationQueryStrategy}.
 * It applies authorization to a search query by fetching the authorized resources for the
 * authenticated user and creating a new search query with the authorization applied.
 */
public class DocumentAuthorizationQueryStrategy implements AuthorizationQueryStrategy {

  private final AuthorizationChecker authorizationChecker;

  public DocumentAuthorizationQueryStrategy(final AuthorizationChecker authorizationChecker) {
    this.authorizationChecker = authorizationChecker;
  }

  @Override
  public AuthorizationCheck resolveAuthorizationCheck(final SecurityContext securityContext) {
    if (!securityContext.requiresAuthorizationChecks()) {
      return AuthorizationCheck.disabled();
    }
    // fetch the authorization entities for the authenticated user
    final var resourceIds = authorizationChecker.retrieveAuthorizedResourceKeys(securityContext);

    if (resourceIds.contains(WILDCARD)) {
      return AuthorizationCheck.disabled();
    }

    return AuthorizationCheck.enabled(
        withResourceIds(securityContext.authorization(), resourceIds));
  }
}
