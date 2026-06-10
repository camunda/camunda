/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/**
 * Produces {@link MembershipResolver}s that look up the groups, roles, tenants, and mapping rules
 * for an authenticated principal. The resolver is wired into a {@link
 * io.camunda.security.auth.CamundaAuthentication} by the calling converter, which controls which
 * lazy fields are populated.
 */
public interface MembershipService {

  /**
   * Creates a resolver for an OIDC-style principal whose memberships derive from JWT claims plus
   * stored identities. Implementations may eagerly evaluate in-memory work (e.g. parsing groups
   * from a JWT claim) so malformed input fails fast; DB-backed lookups stay deferred until the
   * caller asks for them.
   *
   * @param tokenClaims the raw claims extracted from the authentication token (e.g. JWT/OIDC). Used
   *     to match mapping rules and, when configured, extract groups from the token itself.
   * @param principalId the principal ID to resolve memberships for
   * @param principalType the type of principal (USER or CLIENT)
   * @throws OAuth2AuthenticationException if eager validation of the claims fails
   */
  MembershipResolver newResolver(
      Map<String, Object> tokenClaims, String principalId, PrincipalType principalType)
      throws OAuth2AuthenticationException;

  /**
   * Convenience overload for BASIC-style authentication where there are no token claims to match
   * mapping rules against. Implementations should treat this as a USER principal with empty claims;
   * callers typically skip wiring the {@code mappingRules} supplier on the resulting {@link
   * io.camunda.security.auth.CamundaAuthentication}.
   */
  default MembershipResolver newResolver(final String username) {
    return newResolver(Map.of(), username, PrincipalType.USER);
  }

  enum PrincipalType {
    USER,
    CLIENT
  }

  /**
   * Looks up the membership fields of a single authenticated principal. Each accessor is expected
   * to memoize so concurrent or repeated reads share work; callers wire these as the {@code
   * *Supplier} arguments on the {@code CamundaAuthentication} builder.
   */
  interface MembershipResolver {
    List<String> groups();

    List<String> roles();

    List<String> tenants();

    List<String> mappingRules();
  }
}
