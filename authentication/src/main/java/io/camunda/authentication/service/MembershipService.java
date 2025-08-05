/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.security.auth.CamundaAuthentication;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/**
 * Service interface for resolving user, client, and group memberships based on authentication
 * claims.
 */
public interface MembershipService {

  /**
   * Resolves the memberships (groups, roles, tenants, and mapping rules) for a user or client based
   * on the provided authentication claims.
   *
   * @param tokenClaims the raw claims extracted from the authentication token (e.g., JWT/OIDC
   *     token). These are used for matching against mapping rules and extracting groups from the
   *     token itself
   * @param authenticatedClaims mutable map that will be enriched with authentication context (e.g.,
   *     AUTHORIZED_USERNAME, AUTHORIZED_CLIENT_ID, USER_TOKEN_CLAIMS). These claims are passed to
   *     the broker for authorization decisions
   * @param username the username to resolve memberships for
   * @param clientId the client ID to resolve memberships for
   * @return a {@link CamundaAuthentication} containing the resolved groups, roles, tenants, and
   *     mappings
   * @throws org.springframework.security.oauth2.core.OAuth2AuthenticationException if membership
   *     resolution fails
   */
  CamundaAuthentication resolveMemberships(
      Map<String, Object> tokenClaims,
      Map<String, Object> authenticatedClaims,
      String username,
      String clientId)
      throws OAuth2AuthenticationException;
}
