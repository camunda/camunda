/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import org.jspecify.annotations.NullMarked;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Per-tenant context object handed to {@link PerTenantSecurityChainFactory} when building one PT's
 * webapp or API chain. All fields are tenant-scoped; cluster-shared collaborators (JwtDecoder,
 * LogoutSuccessHandler, AuthorizedClientRepository, SecurityContextRepository) are intentionally
 * NOT on the context object and are wired through their own beans.
 *
 * <p>The {@link AccessPath} discriminator distinguishes the prefixed PT chains (matching {@code
 * /physical-tenant/<id>/**}) from the default tenant's unprefixed chains (matching {@code /**}).
 * The {@link #webappPathPrefix()} helper centralises the URL prefix derivation so the chain factory
 * stays prefix-agnostic.
 */
@NullMarked
public record PhysicalTenantChainContext(
    String tenantId,
    AccessPath accessPath,
    ClientRegistrationRepository clientRegistrationRepository,
    SessionRepositoryFilter<?> sessionRepositoryFilter,
    CookieHttpSessionIdResolver httpSessionIdResolver) {

  public enum AccessPath {
    PREFIXED,
    UNPREFIXED_DEFAULT
  }

  /**
   * URL prefix for this tenant's webapp chain. {@code /physical-tenant/<tenantId>} for prefixed
   * tenants; empty string for the unprefixed default chain.
   */
  public String webappPathPrefix() {
    return accessPath == AccessPath.PREFIXED ? "/physical-tenant/" + tenantId : "";
  }
}
