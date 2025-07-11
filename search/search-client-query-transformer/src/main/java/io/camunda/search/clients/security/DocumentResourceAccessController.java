/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.security;

import io.camunda.search.clients.security.ResourceAccessChecks.AuthorizationChecks;
import io.camunda.search.clients.security.ResourceAccessChecks.TenantChecks;
import io.camunda.search.exception.ResourceAccessForbiddenException;
import io.camunda.search.exception.TenantAccessForbiddenException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class DocumentResourceAccessController implements ResourceAccessController {

  private final ResourceAccessProvider resourceAccessProvider;
  private final TenantAccessProvider tenantAccessProvider;

  public DocumentResourceAccessController(
      final ResourceAccessProvider resourceAccessProvider,
      final TenantAccessProvider tenantAccessProvider) {
    this.resourceAccessProvider = resourceAccessProvider;
    this.tenantAccessProvider = tenantAccessProvider;
  }

  @Override
  public boolean supports(final SecurityContext securityContext) {
    return securityContext.authentication() != null
        && !isAnonymousAuthentication(securityContext.authentication());
  }

  @Override
  public <T> T doGet(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> accessChecksApplier) {
    return doReadPostFiltered(securityContext, accessChecksApplier);
  }

  @Override
  public <T> SearchQueryResult<T> doSearch(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, SearchQueryResult<T>> accessChecksApplier) {
    return doReadPreFiltered(securityContext, accessChecksApplier);
  }

  @Override
  public <T> List<T> doAggregate(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, List<T>> accessChecksApplier) {
    return doReadPreFiltered(securityContext, accessChecksApplier);
  }

  protected <T> T doReadPreFiltered(
      final SecurityContext securityContext, final Function<ResourceAccessChecks, T> applier) {
    final var authentication = securityContext.authentication();
    final var authorization = securityContext.authorization();

    final var resourceAccess = resolveResourcesAccessOrThrow(authentication, authorization);
    final var authorizationChecks = createSearchAuthorizationChecks(resourceAccess);

    final var tenantAccess = resolveTenantAccess(authentication);
    final var tenantChecks = createSearchTenantChecks(tenantAccess);

    // read with resource access checks
    final var resourceAccessChecks =
        ResourceAccessChecks.with(authentication, authorizationChecks, tenantChecks);
    return applier.apply(resourceAccessChecks);
  }

  protected <T> T doReadPostFiltered(
      final SecurityContext securityContext, final Function<ResourceAccessChecks, T> applier) {
    // read without any resource access check
    final T resource = applier.apply(ResourceAccessChecks.disabled());

    if (resource == null) {
      return null;
    }

    // now check the access
    final var authentication = securityContext.authentication();
    final var authorization = (Authorization<T>) securityContext.authorization();

    ensureTenantAccessOrThrow(authentication, resource);
    ensureResourceAccessOrThrow(authentication, authorization, resource);

    return resource;
  }

  protected ResourceAccess resolveResourcesAccessOrThrow(
      final CamundaAuthentication authentication, final Authorization<?> authorization) {
    final var resourceAccess =
        resourceAccessProvider.resolveResourcesAccess(authentication, authorization);
    if (resourceAccess.forbidden()) {
      throw new ResourceAccessForbiddenException(authorization);
    }
    return resourceAccess;
  }

  protected AuthorizationChecks createSearchAuthorizationChecks(
      final ResourceAccess resourceAccess) {
    return Optional.of(resourceAccess)
        .filter(a -> !a.hasWildcardAccess())
        .map(ResourceAccess::authorization)
        .map(AuthorizationChecks::required)
        .orElseGet(AuthorizationChecks::notRequired);
  }

  protected <T> void ensureResourceAccessOrThrow(
      final CamundaAuthentication authentication,
      final Authorization<T> authorization,
      final T document) {
    final var resourceAccess =
        resourceAccessProvider.hasResourceAccess(authentication, authorization, document);
    if (resourceAccess.forbidden()) {
      throw new ResourceAccessForbiddenException(authorization);
    }
  }

  protected TenantAccess resolveTenantAccess(final CamundaAuthentication authentication) {
    return tenantAccessProvider.resolveTenantAccess(authentication);
  }

  protected TenantChecks createSearchTenantChecks(final TenantAccess tenantAccess) {
    // It may be that the principal is not a member of any tenant,
    // but they query none tenant-owned entities (like authorizations)
    // => do tenant check the reader will apply the check accordingly (if required)
    return Optional.of(tenantAccess)
        .filter(t -> !t.hasAccessToAllTenants())
        .map(t -> TenantChecks.required(t.tenantIds()))
        .orElseGet(TenantChecks::notRequired);
  }

  protected <T> void ensureTenantAccessOrThrow(
      final CamundaAuthentication authentication, final T document) {
    final var tenantAccess = tenantAccessProvider.hasTenantAccess(authentication, document);
    if (tenantAccess.forbidden()) {
      throw new TenantAccessForbiddenException("No tenant access");
    }
  }
}
