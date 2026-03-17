/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.adapter;

import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;

import io.camunda.gatekeeper.exception.GatekeeperAuthenticationException;
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.model.identity.CamundaUserInfo;
import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.gatekeeper.spi.CamundaUserProvider;
import io.camunda.gatekeeper.spring.condition.ConditionalOnAuthenticationMethod;
import io.camunda.security.reader.ResourceAccessProvider;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public final class OidcCamundaUserProviderAdapter implements CamundaUserProvider {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final Optional<ResourceAccessProvider> resourceAccessProvider;
  private final OAuth2AuthorizedClientRepository authorizedClientRepository;
  private final HttpServletRequest request;

  public OidcCamundaUserProviderAdapter(
      final CamundaAuthenticationProvider authenticationProvider,
      final Optional<ResourceAccessProvider> resourceAccessProvider,
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final HttpServletRequest request) {
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
    this.authorizedClientRepository = authorizedClientRepository;
    this.request = request;
  }

  @Override
  public CamundaUserInfo getCurrentUser() {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return Optional.ofNullable(authentication)
        .filter(a -> !a.isAnonymous())
        .map(
            auth -> {
              final var user = getUser();
              final var authorizedComponents = getAuthorizedComponents(auth);
              final var tenants =
                  auth.authenticatedTenantIds() != null
                      ? auth.authenticatedTenantIds().stream()
                          .map(id -> new CamundaUserInfo.Tenant(id, null, null))
                          .toList()
                      : List.<CamundaUserInfo.Tenant>of();
              return new CamundaUserInfo(
                  user != null ? user.getFullName() : auth.authenticatedUsername(),
                  auth.authenticatedUsername(),
                  user != null ? user.getEmail() : null,
                  authorizedComponents,
                  tenants,
                  auth.authenticatedGroupIds(),
                  auth.authenticatedRoleIds(),
                  null,
                  Map.of(),
                  true);
            })
        .orElse(null);
  }

  @Override
  public String getUserToken() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    final var oidcUser = getOidcUser(authentication);

    if (oidcUser == null) {
      throw new GatekeeperAuthenticationException(
          "User is not authenticated or is not an OIDC user");
    }

    final var token = getToken(authentication, oidcUser);
    if (token == null) {
      throw new GatekeeperAuthenticationException("User does not have a valid token");
    }
    return token;
  }

  private String getToken(final Authentication authentication, final OidcUser oidcUser) {
    return Optional.ofNullable(getAccessToken(authentication))
        .orElseGet(() -> getIdToken(oidcUser));
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

  private StandardClaimAccessor getUser() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    return Optional.ofNullable(getOidcUser(authentication))
        .map(StandardClaimAccessor.class::cast)
        .orElseGet(() -> getOidcTokenBasedUser(authentication));
  }

  private OidcUser getOidcUser(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .map(Authentication::getPrincipal)
        .filter(OidcUser.class::isInstance)
        .map(OidcUser.class::cast)
        .orElse(null);
  }

  private StandardClaimAccessor getOidcTokenBasedUser(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .filter(AbstractOAuth2TokenAuthenticationToken.class::isInstance)
        .map(AbstractOAuth2TokenAuthenticationToken.class::cast)
        .map(AbstractOAuth2TokenAuthenticationToken::getTokenAttributes)
        .map(OidcTokenUser::new)
        .orElse(null);
  }

  private OAuth2AuthorizedClient getAuthorizedClient(
      final OAuth2AuthenticationToken authenticationToken) {
    final var clientRegistrationId = authenticationToken.getAuthorizedClientRegistrationId();
    return authorizedClientRepository.loadAuthorizedClient(
        clientRegistrationId, authenticationToken, request);
  }

  private List<String> getAuthorizedComponents(
      final io.camunda.gatekeeper.model.identity.CamundaAuthentication authentication) {
    if (resourceAccessProvider.isEmpty()) {
      return List.of();
    }
    final var componentAccess =
        resourceAccessProvider
            .get()
            .resolveResourceAccess(authentication, COMPONENT_ACCESS_AUTHORIZATION);
    return componentAccess.allowed() ? componentAccess.authorization().resourceIds() : List.of();
  }

  record OidcTokenUser(Map<String, Object> claims) implements StandardClaimAccessor {

    @Override
    public Map<String, Object> getClaims() {
      return claims;
    }
  }
}
