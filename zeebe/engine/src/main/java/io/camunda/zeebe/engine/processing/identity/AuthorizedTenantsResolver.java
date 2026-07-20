/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.api.context.TokenClaimsAuthenticationResolver;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.auth.Authorization;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves the {@link AuthorizedTenants} for a command from its authorization claims.
 *
 * <p>Centralizes the tenant-resolution logic so the Job and UserTask processor domains cannot
 * diverge. Mirrors the behavior of the pre-CSL-migration {@code TenantResolver}: when multi-tenancy
 * is disabled, any tenant is accessible (no tenant enforcement).
 */
@NullMarked
public final class AuthorizedTenantsResolver {

  private AuthorizedTenantsResolver() {}

  public static AuthorizedTenants resolve(
      final Map<String, Object> authorizations,
      final EngineSecurityConfig securityConfig,
      final TokenClaimsAuthenticationResolver claimsConverter) {
    if (Boolean.TRUE.equals(authorizations.get(Authorization.AUTHORIZED_ANONYMOUS_USER))) {
      return AuthorizedTenants.ANONYMOUS;
    }
    if (!securityConfig.isMultiTenancyChecksEnabled()) {
      return AuthorizedTenants.ANONYMOUS;
    }
    if (authorizations.get(Authorization.AUTHORIZED_USERNAME) == null
        && authorizations.get(Authorization.AUTHORIZED_CLIENT_ID) == null) {
      return new AuthenticatedAuthorizedTenants(List.of());
    }
    return new AuthorizedTenantsAdapter(claimsConverter.resolve(authorizations));
  }
}
