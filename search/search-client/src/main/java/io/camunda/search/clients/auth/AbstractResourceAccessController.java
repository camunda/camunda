/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.search.exception.ErrorMessages.ERROR_RESOURCE_ACCESS_DOES_NOT_CONTAIN_AUTHORIZATION;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.ErrorMessages;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.exception.TenantAccessDeniedException;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.ResourceAccessController;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.security.reader.TenantAccess;
import io.camunda.security.reader.TenantAccessProvider;
import io.camunda.security.reader.TenantCheck;
import java.util.Optional;
import java.util.function.Function;

public abstract class AbstractResourceAccessController implements ResourceAccessController {

  protected abstract ResourceAccessProvider getResourceAccessProvider();

  protected abstract TenantAccessProvider getTenantAccessProvider();

  @Override
  public <T> T doGet(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    final var authentication = securityContext.authentication();
    final var authorization = (Authorization<T>) securityContext.authorization();
    return doPostFiltering(authentication, authorization, resourceChecksApplier);
  }

  @Override
  public <T> T doSearch(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    final var authentication = securityContext.authentication();
    final var authorization = securityContext.authorization();
    return doPreFiltering(authentication, authorization, resourceChecksApplier);
  }

  @Override
  public boolean supports(final SecurityContext securityContext) {
    return Optional.of(securityContext)
            .filter(c -> c.authentication() != null && c.authorization() != null)
            .isPresent()
        && !isAnonymousAuthentication(securityContext.authentication());
  }

  protected <T> T doPreFiltering(
      final CamundaAuthentication authentication,
      final Authorization<?> authorization,
      final Function<ResourceAccessChecks, T> applier) {
    final var authorizationCheck = determineAuthorizationCheck(authentication, authorization);
    final var tenantCheck = determineTenantCheck(authentication);

    // read with resource access checks
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);
    return applier.apply(resourceAccessChecks);
  }

  protected AuthorizationCheck determineAuthorizationCheck(
      final CamundaAuthentication authentication, final Authorization<?> authorization) {
    final var resourceAccess = resolveResourcesAccess(authentication, authorization);
    return createAuthorizationCheck(resourceAccess);
  }

  protected ResourceAccess resolveResourcesAccess(
      final CamundaAuthentication authentication, final Authorization<?> authorization) {
    return getResourceAccessProvider().resolveResourceAccess(authentication, authorization);
  }

  protected AuthorizationCheck createAuthorizationCheck(final ResourceAccess resourceAccess) {
    return Optional.of(resourceAccess)
        .filter(f -> !f.wildcard())
        .map(
            r ->
                Optional.ofNullable(r.authorization())
                    .map(AuthorizationCheck::enabled)
                    .orElseThrow(
                        () ->
                            new CamundaSearchException(
                                ERROR_RESOURCE_ACCESS_DOES_NOT_CONTAIN_AUTHORIZATION.formatted(
                                    resourceAccess))))
        .orElseGet(AuthorizationCheck::disabled);
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
      final CamundaAuthentication authentication,
      final Authorization<T> authorization,
      final Function<ResourceAccessChecks, T> applier) {
    // read without any resource access check
    final T resource = applier.apply(ResourceAccessChecks.disabled());

    if (resource == null) {
      return null;
    }

    // now ensure access to resource
    ensureTenantAccessOrThrow(authentication, resource);
    ensureResourceAccessOrThrow(authentication, authorization, resource);

    return resource;
  }

  protected <T> void ensureResourceAccessOrThrow(
      final CamundaAuthentication authentication,
      final Authorization<T> authorization,
      final T document) {
    final var resourceAccess =
        getResourceAccessProvider().hasResourceAccess(authentication, authorization, document);
    if (resourceAccess.denied()) {
      throw new ResourceAccessDeniedException(authorization);
    }
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
