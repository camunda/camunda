/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.oidc;

import java.util.Map;

/**
 * Resolves the final claims map used for authorization from a validated JWT's claims and the raw
 * token value. Implementations may augment the JWT claims with the OIDC UserInfo response, subject
 * to provider-specific configuration, caching, and failure-handling behaviour. Specifically,
 * implementations choose whether to fail open (return JWT-only claims on UserInfo failure) or fail
 * closed (propagate an exception); see the concrete implementation's Javadoc for its policy.
 */
public interface OidcClaimsProvider {

  /**
   * @param jwtClaims the claims as extracted from the validated JWT access token
   * @param tokenValue the raw bearer token string (needed if UserInfo must be called)
   * @return the claims map to be used for principal and authorization resolution. Fail-open
   *     implementations may return the original JWT claims unchanged when UserInfo augmentation is
   *     unavailable; fail-closed implementations may throw to reject the request.
   */
  Map<String, Object> claimsFor(Map<String, Object> jwtClaims, String tokenValue);
}
