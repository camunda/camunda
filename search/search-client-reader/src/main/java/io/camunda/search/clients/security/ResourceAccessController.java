/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.security;

import io.camunda.search.exception.ResourceAccessForbiddenException;
import io.camunda.search.exception.TenantAccessForbiddenException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.zeebe.auth.Authorization;
import java.util.List;
import java.util.function.Function;

public interface ResourceAccessController {

  default boolean supports(final SecurityContext securityContext) {
    return false;
  }

  <T> T doGet(
      final SecurityContext securityContext, Function<ResourceAccessChecks, T> accessChecksApplier)
      throws ResourceAccessForbiddenException, TenantAccessForbiddenException;

  <T> SearchQueryResult<T> doSearch(
      final SecurityContext securityContext,
      Function<ResourceAccessChecks, SearchQueryResult<T>> accessChecksApplier)
      throws ResourceAccessForbiddenException, TenantAccessForbiddenException;

  <T> List<T> doAggregate(
      final SecurityContext securityContext,
      Function<ResourceAccessChecks, List<T>> accessChecksApplier)
      throws ResourceAccessForbiddenException, TenantAccessForbiddenException;

  default boolean isAnonymousAuthentication(final CamundaAuthentication authentication) {
    final var claims = authentication.claims();
    if (claims != null && claims.containsKey(Authorization.AUTHORIZED_ANONYMOUS_USER)) {
      return ((boolean) claims.get(Authorization.AUTHORIZED_ANONYMOUS_USER));
    }
    return false;
  }
}
