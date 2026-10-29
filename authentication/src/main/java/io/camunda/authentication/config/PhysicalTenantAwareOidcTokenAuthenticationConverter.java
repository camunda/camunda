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
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Selects the REST bearer-token converter for the physical tenant a request targets (via {@link
 * PhysicalTenantContext#currentOrNull()}, stamped by {@code PhysicalTenantFilter} before Spring
 * Security runs), falling back to {@code defaultConverter} when the request carries no tenant stamp
 * or names a tenant without a dedicated converter (including the unprefixed {@code /v2} cluster
 * surface, which resolves to the {@code default} tenant).
 *
 * <p>Selection is keyed by physical tenant, not by issuer: two tenants may legitimately share one
 * OIDC issuer yet map identity from different claims, so the issuer alone cannot disambiguate them.
 * This is the bearer-token counterpart of the per-provider login converter and the per-tenant gRPC
 * interceptor (camunda/camunda#64685).
 *
 * <p>The tenant's converter is looked up on every request, so a tenant added, replaced or removed
 * at runtime is mapped with the converter of the configuration it is served with now.
 */
public final class PhysicalTenantAwareOidcTokenAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private static final Logger LOG =
      LoggerFactory.getLogger(PhysicalTenantAwareOidcTokenAuthenticationConverter.class);

  private final Function<String, Optional<CamundaAuthenticationConverter<Authentication>>>
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
    this(
        lookup(
            Map.copyOf(
                Objects.requireNonNull(convertersByPhysicalTenant, "convertersByPhysicalTenant"))),
        defaultConverter);
  }

  /**
   * @param convertersByPhysicalTenant looks up the converter of a physical tenant at the time of
   *     the request
   * @param defaultConverter used when the current request carries no physical-tenant id, or names a
   *     tenant with no dedicated converter
   */
  public PhysicalTenantAwareOidcTokenAuthenticationConverter(
      final Function<String, Optional<CamundaAuthenticationConverter<Authentication>>>
          convertersByPhysicalTenant,
      final CamundaAuthenticationConverter<Authentication> defaultConverter) {
    this.convertersByPhysicalTenant =
        Objects.requireNonNull(convertersByPhysicalTenant, "convertersByPhysicalTenant");
    this.defaultConverter = Objects.requireNonNull(defaultConverter, "defaultConverter");
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return authentication instanceof JwtAuthenticationToken;
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    final String physicalTenantId = PhysicalTenantContext.currentOrNull();
    final CamundaAuthenticationConverter<Authentication> dedicated =
        physicalTenantId == null
            ? null
            : convertersByPhysicalTenant.apply(physicalTenantId).orElse(null);
    if (LOG.isDebugEnabled()) {
      // The symptom of a misrouted token is a bare 401, indistinguishable from any other rejection;
      // record which tenant resolved and whether it used its own converter so a typo'd tenant id or
      // deployment drift is diagnosable without a debugger.
      LOG.debug(
          "Resolving REST bearer token for physical tenant '{}' using the {} claim converter",
          physicalTenantId == null ? "<none>" : physicalTenantId,
          dedicated != null ? "tenant-specific" : "default");
    }
    return (dedicated != null ? dedicated : defaultConverter).convert(authentication);
  }

  private static Function<String, Optional<CamundaAuthenticationConverter<Authentication>>> lookup(
      final Map<String, CamundaAuthenticationConverter<Authentication>> converters) {
    return physicalTenantId -> Optional.ofNullable(converters.get(physicalTenantId));
  }
}
