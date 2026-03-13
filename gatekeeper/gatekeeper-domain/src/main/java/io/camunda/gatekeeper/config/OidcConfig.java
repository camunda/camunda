/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.config;

import java.time.Duration;
import java.util.List;

/** Immutable OIDC configuration record for the domain layer. */
public record OidcConfig(
    String issuerUri,
    String clientId,
    String clientSecret,
    String jwkSetUri,
    List<String> additionalJwkSetUris,
    String authorizationUri,
    String tokenUri,
    String endSessionEndpointUri,
    String usernameClaim,
    String clientIdClaim,
    String groupsClaim,
    boolean preferUsernameClaim,
    String scope,
    List<String> audiences,
    String redirectUri,
    Duration clockSkew,
    boolean idpLogoutEnabled,
    String grantType,
    String clientAuthenticationMethod,
    String registrationId) {

  public OidcConfig {
    additionalJwkSetUris =
        additionalJwkSetUris != null ? List.copyOf(additionalJwkSetUris) : List.of();
    audiences = audiences != null ? List.copyOf(audiences) : List.of();
  }

  /** Returns true if the groups claim is configured (not null and not blank). */
  public boolean isGroupsClaimConfigured() {
    return groupsClaim != null && !groupsClaim.isBlank();
  }
}
