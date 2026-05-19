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
 * Produces {@link MembershipResolver}s for OIDC-authenticated principals. Memberships derive from
 * JWT claims plus stored identities; implementations may eagerly evaluate in-memory work (e.g.
 * parsing groups from a JWT claim) so malformed input fails fast, while DB-backed lookups stay
 * deferred until the caller asks for them.
 */
public interface OidcMembershipService {

  /**
   * @param tokenClaims the raw claims extracted from the authentication token (e.g. JWT/OIDC). Used
   *     to match mapping rules and, when configured, extract groups from the token itself.
   * @param principalId the principal ID to resolve memberships for
   * @param principalType the type of principal (USER or CLIENT)
   * @throws OAuth2AuthenticationException if eager validation of the claims fails
   */
  MembershipResolver newResolver(
      Map<String, Object> tokenClaims, String principalId, PrincipalType principalType)
      throws OAuth2AuthenticationException;

  enum PrincipalType {
    USER,
    CLIENT
  }
}
