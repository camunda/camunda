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
 * Per-tenant data carried into {@link PerTenantSecurityChainFactory#buildWebappChain}. All fields
 * are tenant-scoped; cluster-shared collaborators (JwtDecoder, LogoutSuccessHandler,
 * AuthorizedClientRepository, SecurityContextRepository) are intentionally NOT on the slice and are
 * wired through their own beans.
 *
 * <p>The {@link AccessPath} discriminator is forward-looking for Task 12 (an unprefixed default
 * chain at {@code /**}). Today only {@link AccessPath#PREFIXED} is exercised; including it now so
 * the slice shape is stable across Tasks 6–12.
 */
@NullMarked
public record TenantSecuritySlice(
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
   * tenants; empty string for the unprefixed default chain (Task 12).
   */
  public String webappPathPrefix() {
    return accessPath == AccessPath.PREFIXED ? "/physical-tenant/" + tenantId : "";
  }
}
