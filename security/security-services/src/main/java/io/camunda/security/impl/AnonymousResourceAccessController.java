/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.security.ResourceAccessController;
import io.camunda.search.exception.ResourceAccessForbiddenException;
import io.camunda.search.exception.TenantAccessForbiddenException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.SecurityContext;
import java.util.List;
import java.util.function.Function;

public class AnonymousResourceAccessController implements ResourceAccessController {

  @Override
  public boolean supports(final SecurityContext securityContext) {
    return securityContext.authentication() != null
        && isAnonymousAuthentication(securityContext.authentication());
  }

  @Override
  public <T> T doGet(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> accessChecksApplier)
      throws ResourceAccessForbiddenException, TenantAccessForbiddenException {
    return doReadAnonymously(accessChecksApplier);
  }

  @Override
  public <T> SearchQueryResult<T> doSearch(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, SearchQueryResult<T>> accessChecksApplier)
      throws ResourceAccessForbiddenException, TenantAccessForbiddenException {
    return doReadAnonymously(accessChecksApplier);
  }

  @Override
  public <T> List<T> doAggregate(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, List<T>> accessChecksApplier)
      throws ResourceAccessForbiddenException, TenantAccessForbiddenException {
    return doReadAnonymously(accessChecksApplier);
  }

  protected <X> X doReadAnonymously(final Function<ResourceAccessChecks, X> applier) {
    return applier.apply(ResourceAccessChecks.disabled());
  }
}
