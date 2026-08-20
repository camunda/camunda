/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.security.api.context.TokenClaimsAuthenticationResolver;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import io.camunda.security.api.model.authz.AuthorizationRejection;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.ScopedAuthorizationCheckPortFactory.ScopedAuthorizationCheckPorts;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.List;
import java.util.Map;

/**
 * {@link AuthorizationCheckPort} that resolves the per-physical-tenant {@link
 * AuthorizationCheckPort} assembled by {@link
 * io.camunda.security.core.authz.ScopedAuthorizationCheckPortFactory} for the physical tenant of
 * the current request, and additionally grants COMPONENT/ACCESS on {@code admin} to any principal
 * holding the legacy {@code identity} component grant.
 *
 * <p>The {@code identity}-to-{@code admin} alias exists because the Identity web app was renamed to
 * Admin; hosts with pre-existing {@code identity} component grants must keep working against the
 * {@code admin} component id without a data migration. It replaces the legacy {@code
 * IdentityToAdminComponentAliasAdapter} (issue #399).
 *
 * <p>{@code hasPerTenantScopes} distinguishes the two wiring shapes: when secondary storage is
 * disabled, {@code checkPorts} was assembled from a single {@code default}-keyed entry and every
 * request — regardless of what {@link PhysicalTenantContext#currentOrNull()} returns, typically
 * {@code null} — resolves to it; when per-tenant scopes exist, the physical tenant id is passed
 * through unchanged (including {@code null}) so an unstamped request fails hard rather than
 * silently resolving against the default tenant's storage.
 */
final class TenantAwareAuthorizationCheckPort implements AuthorizationCheckPort {

  private static final String COMPONENT_ADMIN = "admin";
  private static final String COMPONENT_IDENTITY_LEGACY_ALIAS = "identity";

  private final ScopedAuthorizationCheckPorts checkPorts;
  private final boolean hasPerTenantScopes;
  private final TokenClaimsAuthenticationResolver claimsResolver;

  TenantAwareAuthorizationCheckPort(
      final ScopedAuthorizationCheckPorts checkPorts,
      final boolean hasPerTenantScopes,
      final TokenClaimsAuthenticationResolver claimsResolver) {
    this.checkPorts = checkPorts;
    this.hasPerTenantScopes = hasPerTenantScopes;
    this.claimsResolver = claimsResolver;
  }

  @Override
  public <T> Either<AuthorizationRejection, Void> check(
      final CamundaAuthentication authentication, final RequiredAuthorization<T> authorization) {
    final AuthorizationCheckPort checkPort = checkPortForCurrentScope();
    final Either<AuthorizationRejection, Void> result =
        checkPort.check(authentication, authorization);
    if (result.isRight() || !isComponentAdminAccess(authorization)) {
      return result;
    }
    final Either<AuthorizationRejection, Void> aliasResult =
        checkPort.check(authentication, withIdentityAlias(authorization));
    // If both checks fail, return the original rejection: it names the requested "admin"
    // component, whereas aliasResult's would reference the internal "identity" alias.
    return aliasResult.isRight() ? aliasResult : result;
  }

  @Override
  public <T> Either<AuthorizationRejection, Void> check(
      final Map<String, Object> claims, final RequiredAuthorization<T> authorization) {
    return check(claimsResolver.resolve(claims), authorization);
  }

  @Override
  public <T> Either<AuthorizationRejection, Void> check(
      final CamundaAuthentication authentication,
      final RequiredAuthorization<T> authorization,
      final T resource) {
    return checkPortForCurrentScope().check(authentication, authorization, resource);
  }

  private AuthorizationCheckPort checkPortForCurrentScope() {
    if (!hasPerTenantScopes) {
      return checkPorts.forScope(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    }
    return checkPorts.forScope(PhysicalTenantContext.currentOrNull());
  }

  private static <T> boolean isComponentAdminAccess(final RequiredAuthorization<T> authorization) {
    return authorization.resourceType() == AuthorizationResourceType.COMPONENT
        && authorization.permissionType() == PermissionType.ACCESS
        && authorization.resourceIds() != null
        && authorization.resourceIds().contains(COMPONENT_ADMIN);
  }

  private static <T> RequiredAuthorization<T> withIdentityAlias(
      final RequiredAuthorization<T> authorization) {
    final List<String> aliasedResourceIds =
        authorization.resourceIds().stream()
            .map(id -> COMPONENT_ADMIN.equals(id) ? COMPONENT_IDENTITY_LEGACY_ALIAS : id)
            .toList();
    return authorization.withResourceIds(aliasedResourceIds);
  }
}
