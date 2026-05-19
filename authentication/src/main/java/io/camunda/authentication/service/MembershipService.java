/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/**
 * Produces {@link MembershipProvider}s for an authenticated principal. A principal is described by
 * its ID, a type (USER or CLIENT), and a (possibly empty) map of claims carried by the
 * authentication token. Implementations are selected per authentication method (via
 * {@code @ConditionalOnAuthenticationMethod}) so a given Spring context has at most one bean of
 * this type.
 */
public interface MembershipService {

  /**
   * Resolves memberships for a principal. Implementations may eagerly evaluate in-memory work (e.g.
   * parsing groups from a JWT claim) so malformed input fails fast; DB-backed lookups stay deferred
   * until the caller asks for them.
   *
   * @param principalId the principal ID
   * @param type the type of principal (USER or CLIENT)
   * @param claims the raw claims carried by the authentication token, or an empty map when none are
   *     available (e.g. BASIC auth)
   * @throws OAuth2AuthenticationException if eager validation of the claims fails
   */
  MembershipProvider createProvider(
      String principalId, PrincipalType type, Map<String, Object> claims)
      throws OAuth2AuthenticationException;

  /**
   * Convenience for callers whose principal is a USER with no claims (e.g. BASIC auth). Delegates
   * to {@link #createProvider(String, PrincipalType, Map)} with {@link PrincipalType#USER} and an
   * empty claims map.
   */
  default MembershipProvider createProviderForUser(final String principalId) {
    return createProvider(principalId, PrincipalType.USER, Map.of());
  }

  enum PrincipalType {
    USER,
    CLIENT
  }
}
