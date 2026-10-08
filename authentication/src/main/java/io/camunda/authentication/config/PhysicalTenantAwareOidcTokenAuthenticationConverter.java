/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Resolves a REST bearer token's {@link CamundaAuthentication} using the claim configuration of the
 * <em>physical tenant the request targets</em>, not the cluster-default one.
 *
 * <p>This is the bearer-token counterpart of {@link ProviderAwareOidcUserAuthenticationConverter}
 * (the interactive-login converter): login selects the per-provider claim config by OAuth2
 * registration id, and gRPC resolves per tenant in its own interceptor. The REST bearer path
 * previously had no such selection — every token was converted with the root/default claim config —
 * which rejected a tenant's own token with {@code 401} when that tenant's IdP used different {@code
 * username-claim}/{@code client-id-claim} settings (camunda/camunda#64685).
 *
 * <p>Selection is keyed by physical tenant (via {@link PhysicalTenantContext#currentOrNull()},
 * which {@code PhysicalTenantFilter} stamps before Spring Security runs), not by issuer: two
 * tenants may legitimately share one OIDC issuer yet configure different claims, so the issuer
 * alone cannot disambiguate them. A request whose tenant has no dedicated converter (including the
 * unprefixed {@code /v2} cluster surface, which resolves to the {@code default} tenant) falls back
 * to the supplied default converter.
 */
public final class PhysicalTenantAwareOidcTokenAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final Map<String, CamundaAuthenticationConverter<Authentication>>
      convertersByPhysicalTenant;
  private final CamundaAuthenticationConverter<Authentication> defaultConverter;

  /**
   * @param convertersByPhysicalTenant per-tenant converters keyed by physical-tenant id (each built
   *     from that tenant's resolved OIDC claim configuration)
   * @param defaultConverter used when the current request carries no physical-tenant id, or names a
   *     tenant with no dedicated converter
   */
  public PhysicalTenantAwareOidcTokenAuthenticationConverter(
      final Map<String, CamundaAuthenticationConverter<Authentication>> convertersByPhysicalTenant,
      final CamundaAuthenticationConverter<Authentication> defaultConverter) {
    this.convertersByPhysicalTenant =
        Map.copyOf(
            Objects.requireNonNull(convertersByPhysicalTenant, "convertersByPhysicalTenant"));
    this.defaultConverter = Objects.requireNonNull(defaultConverter, "defaultConverter");
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return authentication instanceof JwtAuthenticationToken;
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    final String physicalTenantId = PhysicalTenantContext.currentOrNull();
    final CamundaAuthenticationConverter<Authentication> converter =
        physicalTenantId == null
            ? defaultConverter
            : convertersByPhysicalTenant.getOrDefault(physicalTenantId, defaultConverter);
    return converter.convert(authentication);
  }
}
