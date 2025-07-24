/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.security;

import io.camunda.search.exception.ResourceAccessDeniedException;
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

// TODO: This is now identical to the DocumentResourceAccessController, should probably be unified
public class RdbmsResourceAccessController implements ResourceAccessController {

  private final ResourceAccessProvider resourceAccessProvider;
  private final TenantAccessProvider tenantAccessProvider;

  public RdbmsResourceAccessController(
      final ResourceAccessProvider resourceAccessProvider,
      final TenantAccessProvider tenantAccessProvider) {
    this.resourceAccessProvider = resourceAccessProvider;
    this.tenantAccessProvider = tenantAccessProvider;
  }

  @Override
  public <T> T doGet(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return resourceChecksApplier.apply(ResourceAccessChecks.disabled());
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
    return Optional.of(securityContext).map(SecurityContext::authentication).isPresent()
        && !isAnonymousAuthentication(securityContext.authentication());
  }

  protected <T> T doPreFiltering(
      final CamundaAuthentication authentication,
      final Authorization authorization,
      final Function<ResourceAccessChecks, T> applier) {
    final var authorizationCheck = determineAuthorizationCheck(authentication, authorization);
    final var tenantCheck = determineTenantCheck(authentication);

    // read with resource access checks
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);
    return applier.apply(resourceAccessChecks);
  }

  protected AuthorizationCheck determineAuthorizationCheck(
      final CamundaAuthentication authentication, final Authorization authorization) {
    final var resourceAccess = resolveResourcesAccess(authentication, authorization);
    return createAuthorizationCheckIfAccessAllowed(resourceAccess);
  }

  protected ResourceAccess resolveResourcesAccess(
      final CamundaAuthentication authentication, final Authorization authorization) {
    return resourceAccessProvider.resolveResourceAccess(authentication, authorization);
  }

  protected AuthorizationCheck createAuthorizationCheckIfAccessAllowed(
      final ResourceAccess resourceAccess) {
    return Optional.of(resourceAccess)
        .filter(ResourceAccess::allowed)
        .map(
            a ->
                Optional.of(a)
                    .filter(f -> !f.wildcard())
                    .map(ResourceAccess::authorization)
                    .map(AuthorizationCheck::enabled)
                    .orElseGet(AuthorizationCheck::disabled))
        .orElseThrow(() -> new ResourceAccessDeniedException(resourceAccess.authorization()));
  }

  protected TenantCheck determineTenantCheck(final CamundaAuthentication authentication) {
    final var resourceAccess = resolveTenantAccess(authentication);
    return createTenantCheck(resourceAccess);
  }

  protected TenantAccess resolveTenantAccess(final CamundaAuthentication authentication) {
    return tenantAccessProvider.resolveTenantAccess(authentication);
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
}
