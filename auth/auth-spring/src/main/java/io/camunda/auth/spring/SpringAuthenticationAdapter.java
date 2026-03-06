/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring;

import io.camunda.auth.domain.port.inbound.AuthenticationPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Spring Security adapter implementing {@link AuthenticationPort} by reading from the {@link
 * SecurityContextHolder}. Supports both JWT resource server tokens and OIDC login sessions.
 */
public class SpringAuthenticationAdapter implements AuthenticationPort {

  @Override
  public Optional<String> getCurrentUsername() {
    return getJwt()
        .map(
            jwt -> {
              // Standard OIDC claim first, then fallback to sub
              final String preferred = jwt.getClaimAsString("preferred_username");
              return preferred != null ? preferred : jwt.getSubject();
            });
  }

  @Override
  public Optional<String> getCurrentClientId() {
    return getJwt()
        .map(
            jwt -> {
              // Standard OAuth2 claims: azp (authorized party) or client_id
              final String azp = jwt.getClaimAsString("azp");
              return azp != null ? azp : jwt.getClaimAsString("client_id");
            });
  }

  @Override
  public Optional<String> getCurrentToken() {
    return getJwt().map(Jwt::getTokenValue);
  }

  @Override
  public List<String> getCurrentGroupIds() {
    return getClaimAsList("groups");
  }

  @Override
  public List<String> getCurrentRoleIds() {
    return getClaimAsList("roles");
  }

  @Override
  public List<String> getCurrentTenantIds() {
    return getClaimAsList("tenants");
  }

  @Override
  public Map<String, Object> getCurrentClaims() {
    return getJwt().map(Jwt::getClaims).orElse(Map.of());
  }

  @Override
  public boolean isAuthenticated() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated();
  }

  private List<String> getClaimAsList(final String claimName) {
    return getJwt().map(jwt -> jwt.getClaimAsStringList(claimName)).orElse(List.of());
  }

  private Optional<Jwt> getJwt() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      return Optional.of(jwtAuth.getToken());
    }
    // Support OIDC login (e.g., Spring Security OAuth2 Login)
    if (auth != null && auth.getPrincipal() instanceof OidcUser oidcUser) {
      return Optional.ofNullable(oidcUser.getIdToken())
          .map(
              idToken ->
                  Jwt.withTokenValue(idToken.getTokenValue())
                      .headers(h -> h.putAll(Map.of("alg", "none")))
                      .claims(c -> c.putAll(idToken.getClaims()))
                      .build());
    }
    return Optional.empty();
  }
}
