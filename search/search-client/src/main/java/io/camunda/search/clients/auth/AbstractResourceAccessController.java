/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import io.camunda.search.exception.ErrorMessages;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.exception.TenantAccessDeniedException;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.auth.condition.AnyOfAuthorizationCondition;
import io.camunda.security.auth.condition.AuthorizationConditions;
import io.camunda.security.auth.condition.SingleAuthorizationCondition;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.ResourceAccessController;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.security.reader.TenantAccess;
import io.camunda.security.reader.TenantAccessProvider;
import io.camunda.security.reader.TenantCheck;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;

public abstract class AbstractResourceAccessController implements ResourceAccessController {

  protected abstract ResourceAccessProvider getResourceAccessProvider();

  protected abstract TenantAccessProvider getTenantAccessProvider();

  @Override
  public <T> T doGet(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return doPostFiltering(securityContext, resourceChecksApplier);
  }

  @Override
  public <T> T doSearch(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return doPreFiltering(securityContext, resourceChecksApplier);
  }

  @Override
  public boolean supports(final SecurityContext securityContext) {
    return Optional.of(securityContext)
            .filter(c -> c.authentication() != null)
            .filter(c -> c.authorizationCondition() != null)
            .isPresent()
        && !isAnonymousAuthentication(securityContext.authentication());
  }

  protected <T> T doPreFiltering(
      final SecurityContext securityContext, final Function<ResourceAccessChecks, T> applier) {
    final var authorizationCheck = determineAuthorizationCheck(securityContext);
    final var tenantCheck = determineTenantCheck(securityContext.authentication());

    // read with resource access checks
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);
    return applier.apply(resourceAccessChecks);
  }

  protected AuthorizationCheck determineAuthorizationCheck(final SecurityContext securityContext) {
    final var authentication = securityContext.authentication();
    final var condition = securityContext.authorizationCondition();
    return switch (condition) {
      case SingleAuthorizationCondition single ->
          createSingleAuthorizationCheck(authentication, single);
      case AnyOfAuthorizationCondition anyOf ->
          createAnyOfAuthorizationCheck(authentication, anyOf);
      default ->
          throw new IllegalStateException(
              "Unsupported AuthorizationCondition type: " + condition.getClass().getSimpleName());
    };
  }

  protected ResourceAccess resolveResourceAccess(
      final CamundaAuthentication authentication, final Authorization<?> authorization) {
    return getResourceAccessProvider().resolveResourceAccess(authentication, authorization);
  }

  private AuthorizationCheck createSingleAuthorizationCheck(
      final CamundaAuthentication authentication, final SingleAuthorizationCondition single) {
    final var resourceAccess = resolveResourceAccess(authentication, single.authorization());

    if (resourceAccess.wildcard()) {
      return AuthorizationCheck.disabled();
    }

    return AuthorizationCheck.enabled(resourceAccess.authorization());
  }

  private AuthorizationCheck createAnyOfAuthorizationCheck(
      final CamundaAuthentication authentication, final AnyOfAuthorizationCondition anyOf) {
    final var resolvedAuthorizations = new ArrayList<Authorization<?>>();
    for (final Authorization authorization : anyOf.authorizations()) {
      final var resourceAccess = resolveResourceAccess(authentication, authorization);

      if (resourceAccess.wildcard()) {
        return AuthorizationCheck.disabled();
      }

      resolvedAuthorizations.add(resourceAccess.authorization());
    }

    return AuthorizationCheck.enabled(AuthorizationConditions.anyOf(resolvedAuthorizations));
  }

  protected TenantCheck determineTenantCheck(final CamundaAuthentication authentication) {
    final var resourceAccess = resolveTenantAccess(authentication);
    return createTenantCheck(resourceAccess);
  }

  protected TenantAccess resolveTenantAccess(final CamundaAuthentication authentication) {
    return getTenantAccessProvider().resolveTenantAccess(authentication);
  }

  protected TenantCheck createTenantCheck(final TenantAccess tenantAccess) {
    // It may be that the principal is not a member of any tenant,
    // but they query none tenant-owned entities (like authorizations)
    // => even when TenantAccess is denied, enable TenantCheck
    // => the search query will apply accordingly
    return Optional.of(tenantAccess)
        .filter(t -> !t.wildcard())
        .map(a -> TenantCheck.enabled(tenantAccess.tenantIds()))
        .orElseGet(TenantCheck::disabled);
  }

  protected <T> T doPostFiltering(
      final SecurityContext securityContext, final Function<ResourceAccessChecks, T> applier) {
    // read without any resource access check
    final T resource = applier.apply(ResourceAccessChecks.disabled());

    if (resource == null) {
      return null;
    }

    // now ensure access to resource
    ensureTenantAccessOrThrow(securityContext.authentication(), resource);
    ensureResourceAccessOrThrow(securityContext, resource);

    return resource;
  }

  protected <T> void ensureResourceAccessOrThrow(
      final SecurityContext securityContext, final T document) {

    final var condition = securityContext.authorizationCondition();

    switch (condition) {
      case SingleAuthorizationCondition single ->
          ensureSingleAuthorizationAccessOrThrow(securityContext, document, single);
      case AnyOfAuthorizationCondition anyOf ->
          ensureAnyOfAuthorizationAccessOrThrow(securityContext, document, anyOf);
      default ->
          throw new IllegalStateException(
              "Unsupported AuthorizationCondition type: " + condition.getClass().getSimpleName());
    }
  }

  private <T> void ensureSingleAuthorizationAccessOrThrow(
      final SecurityContext securityContext,
      final T document,
      final SingleAuthorizationCondition single) {
    final Authorization authorization = single.authorization();
    final var resourceAccess =
        getResourceAccessProvider()
            .hasResourceAccess(securityContext.authentication(), authorization, document);
    if (resourceAccess.denied()) {
      throw new ResourceAccessDeniedException(authorization);
    }
  }

  private <T> void ensureAnyOfAuthorizationAccessOrThrow(
      final SecurityContext securityContext,
      final T document,
      final AnyOfAuthorizationCondition anyOf) {
    final var authorizations = anyOf.authorizations();
    for (final Authorization authorization : authorizations) {
      final var resourceAccess =
          getResourceAccessProvider()
              .hasResourceAccess(securityContext.authentication(), authorization, document);
      if (resourceAccess.allowed()) {
        // at least one authorization allowed access, no need to check further
        return;
      }
    }

    // none of the authorizations allowed access
    throw new ResourceAccessDeniedException(authorizations);
  }

  protected <T> void ensureTenantAccessOrThrow(
      final CamundaAuthentication authentication, final T document) {
    final var tenantAccess = getTenantAccessProvider().hasTenantAccess(authentication, document);
    if (tenantAccess.denied()) {
      throw new TenantAccessDeniedException(
          ErrorMessages.ERROR_RESOURCE_ACCESS_CONTROLLER_NO_TENANT_ACCESS);
    }
  }
}
