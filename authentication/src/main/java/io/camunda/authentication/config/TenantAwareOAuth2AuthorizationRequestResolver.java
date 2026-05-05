/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.filters.TenantBindingEnforcementFilter;
import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

/**
 * Wraps an underlying {@link OAuth2AuthorizationRequestResolver} (typically {@link
 * ClientAwareOAuth2AuthorizationRequestResolver}) and binds the resolved authorization request to a
 * physical tenant.
 *
 * <p>When the registry is non-empty, the request must include a {@code ?tenant=} query parameter.
 * The pair {@code (tenantId, idpId)} is validated against {@link PhysicalTenantIdpRegistry}; on
 * success the validated tenant id is written to the HTTP session under {@link
 * TenantBindingEnforcementFilter#BOUND_TENANT_ATTRIBUTE}, and the upstream authorization request is
 * returned unchanged so Spring's OIDC flow proceeds with its standard {@code state}/nonce.
 *
 * <p>When the registry is empty (no tenant configuration) the wrapper is a no-op pass-through —
 * preserves backward compatibility for existing OIDC deployments.
 *
 * <p>An invalid {@code (tenantId, idpId)} pair throws {@link IllegalArgumentException}, which the
 * existing {@code OAuth2AuthenticationExceptionHandler} renders as a 400.
 */
public final class TenantAwareOAuth2AuthorizationRequestResolver
    implements OAuth2AuthorizationRequestResolver {

  public static final String TENANT_PARAM = "tenant";

  private final OAuth2AuthorizationRequestResolver delegate;
  private final PhysicalTenantIdpRegistry registry;

  public TenantAwareOAuth2AuthorizationRequestResolver(
      final OAuth2AuthorizationRequestResolver delegate, final PhysicalTenantIdpRegistry registry) {
    this.delegate = delegate;
    this.registry = registry;
  }

  @Override
  public OAuth2AuthorizationRequest resolve(final HttpServletRequest request) {
    final var result = delegate.resolve(request);
    if (result == null) {
      return null;
    }
    return bindTenant(request, result, registrationIdOf(result));
  }

  @Override
  public OAuth2AuthorizationRequest resolve(
      final HttpServletRequest request, final String clientRegistrationId) {
    final var result = delegate.resolve(request, clientRegistrationId);
    if (result == null) {
      return null;
    }
    return bindTenant(request, result, clientRegistrationId);
  }

  private OAuth2AuthorizationRequest bindTenant(
      final HttpServletRequest request,
      final OAuth2AuthorizationRequest authzRequest,
      final String idpId) {

    if (registry.tenantIds().isEmpty()) {
      // BC: no tenant configuration — pass through unchanged.
      return authzRequest;
    }

    final var tenantId = request.getParameter(TENANT_PARAM);
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException(
          "Missing '" + TENANT_PARAM + "' query parameter; tenant-bound login required");
    }

    if (!registry.tenantIds().contains(tenantId)) {
      throw new IllegalArgumentException("Unknown physical tenant: '" + tenantId + "'");
    }

    if (idpId == null || !registry.getIdpsForTenant(tenantId).contains(idpId)) {
      throw new IllegalArgumentException(
          "IdP '" + idpId + "' is not assigned to tenant '" + tenantId + "'");
    }

    request
        .getSession(true)
        .setAttribute(TenantBindingEnforcementFilter.BOUND_TENANT_ATTRIBUTE, tenantId);

    return authzRequest;
  }

  private static String registrationIdOf(final OAuth2AuthorizationRequest authzRequest) {
    final var attributes = authzRequest.getAttributes();
    if (attributes == null) {
      return null;
    }
    final var value = attributes.get(OAuth2ParameterNames.REGISTRATION_ID);
    return value == null ? null : value.toString();
  }
}
