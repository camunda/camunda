/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.user;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.CamundaUserInfo;
import io.camunda.auth.domain.model.TenantInfo;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.domain.spi.CamundaUserProvider;
import io.camunda.auth.domain.spi.TenantInfoProvider;
import io.camunda.auth.domain.spi.WebComponentAccessProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimAccessor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

/**
 * CamundaUserProvider implementation for OIDC auth. Extracts user profile from OIDC claims and uses
 * SPIs for authorization and tenant resolution.
 */
public class OidcCamundaUserProvider implements CamundaUserProvider {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final WebComponentAccessProvider componentAccessProvider;
  private final TenantInfoProvider tenantInfoProvider;
  private final OAuth2AuthorizedClientRepository authorizedClientRepository;
  private final jakarta.servlet.http.HttpServletRequest request;

  public OidcCamundaUserProvider(
      final CamundaAuthenticationProvider authenticationProvider,
      final WebComponentAccessProvider componentAccessProvider,
      final TenantInfoProvider tenantInfoProvider,
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final jakarta.servlet.http.HttpServletRequest request) {
    this.authenticationProvider = authenticationProvider;
    this.componentAccessProvider = componentAccessProvider;
    this.tenantInfoProvider = tenantInfoProvider;
    this.authorizedClientRepository = authorizedClientRepository;
    this.request = request;
  }

  @Override
  public CamundaUserInfo getCurrentUser() {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return Optional.ofNullable(authentication)
        .filter(a -> !a.isAnonymous())
        .map(this::buildUserInfo)
        .orElse(null);
  }

  @Override
  public String getUserToken() {
    final var springAuth = SecurityContextHolder.getContext().getAuthentication();
    final var oidcUser = getOidcUser(springAuth);
    if (oidcUser == null) {
      throw new UnsupportedOperationException("User is not authenticated or is not an OIDC user");
    }
    return Optional.ofNullable(getAccessToken(springAuth)).orElseGet(() -> getIdToken(oidcUser));
  }

  private CamundaUserInfo buildUserInfo(final CamundaAuthentication authentication) {
    final var claimAccessor = getClaimAccessor();
    final var username = authentication.authenticatedUsername();
    final var displayName = claimAccessor != null ? claimAccessor.getFullName() : username;
    final var email = claimAccessor != null ? claimAccessor.getEmail() : null;
    final var tenants = resolveTenants(authentication);
    final var authorizedComponents =
        componentAccessProvider.getAuthorizedComponents(authentication);
    return new CamundaUserInfo(
        displayName,
        username,
        email,
        authorizedComponents,
        tenants,
        authentication.authenticatedGroupIds(),
        authentication.authenticatedRoleIds(),
        true,
        Map.of());
  }

  private StandardClaimAccessor getClaimAccessor() {
    final var springAuth = SecurityContextHolder.getContext().getAuthentication();
    return Optional.ofNullable(getOidcUser(springAuth))
        .map(StandardClaimAccessor.class::cast)
        .orElseGet(() -> getTokenClaimAccessor(springAuth));
  }

  private OidcUser getOidcUser(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .map(Authentication::getPrincipal)
        .filter(OidcUser.class::isInstance)
        .map(OidcUser.class::cast)
        .orElse(null);
  }

  private StandardClaimAccessor getTokenClaimAccessor(final Authentication authentication) {
    if (authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> tokenAuth) {
      final Map<String, Object> claims = tokenAuth.getTokenAttributes();
      return () -> claims;
    }
    return null;
  }

  private String getAccessToken(final Authentication authentication) {
    return Optional.of(authentication)
        .filter(OAuth2AuthenticationToken.class::isInstance)
        .map(OAuth2AuthenticationToken.class::cast)
        .map(this::getAuthorizedClient)
        .map(OAuth2AuthorizedClient::getAccessToken)
        .map(OAuth2AccessToken::getTokenValue)
        .orElse(null);
  }

  private String getIdToken(final OidcUser oidcUser) {
    return Optional.of(oidcUser)
        .map(OidcUser::getIdToken)
        .map(OidcIdToken::getTokenValue)
        .orElse(null);
  }

  private OAuth2AuthorizedClient getAuthorizedClient(
      final OAuth2AuthenticationToken authenticationToken) {
    return authorizedClientRepository.loadAuthorizedClient(
        authenticationToken.getAuthorizedClientRegistrationId(), authenticationToken, request);
  }

  private List<TenantInfo> resolveTenants(final CamundaAuthentication authentication) {
    final var tenantIds = authentication.authenticatedTenantIds();
    if (tenantIds == null || tenantIds.isEmpty()) {
      return List.of();
    }
    return tenantInfoProvider.getTenants(tenantIds);
  }
}
