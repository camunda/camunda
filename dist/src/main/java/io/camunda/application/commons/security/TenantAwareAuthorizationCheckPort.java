/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import io.camunda.security.api.model.authz.AuthorizationRejection;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.security.core.authz.AuthorizationService;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.authz.PropertyAuthorizationEvaluatorRegistry;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.List;
import java.util.Map;

/**
 * {@link AuthorizationCheckPort} that resolves the {@link AuthorizationChecker} for the physical
 * tenant of the current request via {@link AuthorizationCheckerProvider} before delegating to a
 * per-call {@link AuthorizationService}, and additionally grants COMPONENT/ACCESS on {@code admin}
 * to any principal holding the legacy {@code identity} component grant.
 *
 * <p>The {@code identity}-to-{@code admin} alias exists because the Identity web app was renamed to
 * Admin; hosts with pre-existing {@code identity} component grants must keep working against the
 * {@code admin} component id without a data migration. It replaces the legacy {@code
 * IdentityToAdminComponentAliasAdapter} (issue #399).
 */
final class TenantAwareAuthorizationCheckPort implements AuthorizationCheckPort {

  private static final String COMPONENT_ADMIN = "admin";
  private static final String COMPONENT_IDENTITY_LEGACY_ALIAS = "identity";

  private final AuthorizationCheckerProvider authorizationCheckerProvider;
  private final PropertyAuthorizationEvaluatorRegistry propertyEvaluatorRegistry;
  private final boolean authorizationEnabled;
  private final boolean multiTenancyChecksEnabled;
  private final LazyTokenClaimsConverter claimsConverter;

  TenantAwareAuthorizationCheckPort(
      final AuthorizationCheckerProvider authorizationCheckerProvider,
      final PropertyAuthorizationEvaluatorRegistry propertyEvaluatorRegistry,
      final boolean authorizationEnabled,
      final boolean multiTenancyChecksEnabled,
      final LazyTokenClaimsConverter claimsConverter) {
    this.authorizationCheckerProvider = authorizationCheckerProvider;
    this.propertyEvaluatorRegistry = propertyEvaluatorRegistry;
    this.authorizationEnabled = authorizationEnabled;
    this.multiTenancyChecksEnabled = multiTenancyChecksEnabled;
    this.claimsConverter = claimsConverter;
  }

  @Override
  public <T> Either<AuthorizationRejection, Void> check(
      final CamundaAuthentication authentication, final RequiredAuthorization<T> authorization) {
    final AuthorizationService authorizationService = authorizationServiceForCurrentTenant();
    final Either<AuthorizationRejection, Void> result =
        authorizationService.check(authentication, authorization);
    if (result.isRight() || !isComponentAdminAccess(authorization)) {
      return result;
    }
    final Either<AuthorizationRejection, Void> aliasResult =
        authorizationService.check(authentication, withIdentityAlias(authorization));
    // If both checks fail, return the original rejection: it names the requested "admin"
    // component, whereas aliasResult's would reference the internal "identity" alias.
    return aliasResult.isRight() ? aliasResult : result;
  }

  @Override
  public <T> Either<AuthorizationRejection, Void> check(
      final Map<String, Object> claims, final RequiredAuthorization<T> authorization) {
    return check(claimsConverter.convert(claims), authorization);
  }

  @Override
  public <T> Either<AuthorizationRejection, Void> check(
      final CamundaAuthentication authentication,
      final RequiredAuthorization<T> authorization,
      final T resource) {
    return authorizationServiceForCurrentTenant().check(authentication, authorization, resource);
  }

  private AuthorizationService authorizationServiceForCurrentTenant() {
    final AuthorizationChecker checker =
        authorizationCheckerProvider.withPhysicalTenant(PhysicalTenantContext.currentOrNull());
    return new AuthorizationService(
        checker,
        propertyEvaluatorRegistry,
        authorizationEnabled,
        multiTenancyChecksEnabled,
        claimsConverter);
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
