/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.service.authorization.Authorizations.APPLICATION_ACCESS_AUTHORIZATION;

import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.authentication.oauth.ClientAssertionConstants;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.entity.ClusterMetadata.AppName;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.TenantServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import jakarta.json.Json;
import jakarta.json.JsonString;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
@ConditionalOnSecondaryStorageEnabled
@Profile("consolidated-auth")
public class OidcCamundaUserService implements CamundaUserService {
  private static final Logger LOG = LoggerFactory.getLogger(OidcCamundaUserService.class);
  private static final String SALES_PLAN_TYPE = "";

  // TODO: This needs to be set for SaaS purposes
  private static final Map<AppName, String> C8_LINKS = Map.of();

  private final CamundaAuthenticationProvider authenticationProvider;
  private final ResourceAccessProvider resourceAccessProvider;
  private final TenantServices tenantServices;
  private final OAuth2AuthorizedClientRepository authorizedClientRepository;
  private final HttpServletRequest request;

  public OidcCamundaUserService(
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider,
      final TenantServices tenantServices,
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final HttpServletRequest request) {
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
    this.tenantServices = tenantServices;
    this.authorizedClientRepository = authorizedClientRepository;
    this.request = request;
  }

  private Optional<OidcUser> getCamundaUser() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    LOG.info("getCamundaUser() - Authentication: {}", auth);
    if (auth != null) {
      final Object principal = auth.getPrincipal();
      LOG.info(
          "getCamundaUser() - Principal type: {}",
          principal != null ? principal.getClass().getName() : "null");
      LOG.info("getCamundaUser() - Is OidcUser? {}", principal instanceof OidcUser);
    }
    return Optional.ofNullable(auth)
        .map(Authentication::getPrincipal)
        .map(principal -> principal instanceof final OidcUser user ? user : null);
  }

  @Override
  public CamundaUserDTO getCurrentUser() {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return Optional.ofNullable(authentication)
        .filter(a -> !a.isAnonymous())
        .map(this::getCurrentUser)
        .orElse(null);
  }

  @Override
  public String getUserToken() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    final var oidcUser = getOidcUser(authentication);

    if (oidcUser == null) {
      throw new UnsupportedOperationException("User is not authenticated or is not a OIDC user");
    }

    return Optional.ofNullable(getToken(authentication, oidcUser))
        .map(Json::createValue)
        .map(JsonString::toString)
        .orElseThrow(() -> new UnsupportedOperationException("User does not have a valid token"));
  }

  protected String getToken(final Authentication authentication, final OidcUser oidcUser) {
    return Optional.ofNullable(getAccessToken(authentication))
        .orElseGet(() -> getIdToken(oidcUser));
  }

  protected String getAccessToken(final Authentication authentication) {
    return Optional.of(authentication)
        .map(OAuth2AuthenticationToken.class::cast)
        .map(this::getAuthorizedClient)
        .map(OAuth2AuthorizedClient::getAccessToken)
        .map(OAuth2AccessToken::getTokenValue)
        .orElse(null);
  }

  protected String getIdToken(final OidcUser oidcUser) {
    return Optional.of(oidcUser)
        .map(OidcUser::getIdToken)
        .map(OidcIdToken::getTokenValue)
        .orElse(null);
  }

  protected CamundaUserDTO getCurrentUser(final CamundaAuthentication authentication) {
    final var user = getUser();
    final var username = authentication.authenticatedUsername();
    final var groups = authentication.authenticatedGroupIds();
    final var roles = authentication.authenticatedRoleIds();
    final var tenants = getTenantsForCamundaAuthentication(authentication);
    final var authorizedApplications = getAuthorizedApplications(authentication);
    return new CamundaUserDTO(
        user.getFullName(),
        username,
        user.getEmail(),
        authorizedApplications,
        tenants,
        groups,
        roles,
        SALES_PLAN_TYPE,
        C8_LINKS,
        true);
  }

  protected StandardClaimAccessor getUser() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();

    // Check if it's a certificate-based authentication
    if (authentication
            instanceof
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken
        && ClientAssertionConstants.CERT_USER_ID.equals(authentication.getName())) {
      return new CertificateUser();
    }

    // Check if it's JWT-based authentication (from certificate OAuth2 flow)
    if (authentication instanceof JwtAuthenticationToken) {
      return new JwtBasedUser(((JwtAuthenticationToken) authentication).getToken());
    }

    return Optional.ofNullable(getOidcUser(authentication))
        .map(StandardClaimAccessor.class::cast)
        .orElseGet(() -> getOidcTokenBasedUser(authentication));
  }

  protected OidcUser getOidcUser(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .map(Authentication::getPrincipal)
        .filter(OidcUser.class::isInstance)
        .map(OidcUser.class::cast)
        .orElse(null);
  }

  protected StandardClaimAccessor getOidcTokenBasedUser(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .filter(AbstractOAuth2TokenAuthenticationToken.class::isInstance)
        .map(AbstractOAuth2TokenAuthenticationToken.class::cast)
        .map(AbstractOAuth2TokenAuthenticationToken::getTokenAttributes)
        .map(OidcTokenUser::new)
        .orElse(null);
  }

  protected OAuth2AuthorizedClient getAuthorizedClient(
      final OAuth2AuthenticationToken authenticationToken) {
    final var clientRegistrationId = authenticationToken.getAuthorizedClientRegistrationId();
    return authorizedClientRepository.loadAuthorizedClient(
        clientRegistrationId, authenticationToken, request);
  }

  protected List<String> getAuthorizedApplications(final CamundaAuthentication authentication) {
    final var applicationAccess =
        resourceAccessProvider.resolveResourceAccess(
            authentication, APPLICATION_ACCESS_AUTHORIZATION);
    return applicationAccess.allowed()
        ? applicationAccess.authorization().resourceIds()
        : List.of();
  }

  protected List<TenantEntity> getTenantsForCamundaAuthentication(
      final CamundaAuthentication authentication) {
    return Optional.ofNullable(authentication.authenticatedTenantIds())
        .filter(t -> !t.isEmpty())
        .map(this::getTenants)
        .orElseGet(List::of);
  }

  protected List<TenantEntity> getTenants(final List<String> tenantIds) {
    return tenantServices
        .withAuthentication(CamundaAuthentication.anonymous())
        .search(TenantQuery.of(q -> q.filter(f -> f.tenantIds(tenantIds)).unlimited()))
        .items();
  }

  record OidcTokenUser(Map<String, Object> claims) implements StandardClaimAccessor {

    @Override
    public Map<String, Object> getClaims() {
      return claims;
    }
  }

  static class CertificateUser implements StandardClaimAccessor {
    @Override
    public Map<String, Object> getClaims() {
      return Map.of(
          "name",
          ClientAssertionConstants.CERT_USER_NAME,
          "email",
          ClientAssertionConstants.CERT_USER_EMAIL,
          "given_name",
          "-",
          "family_name",
          "-");
    }

    @Override
    public String getFullName() {
      return ClientAssertionConstants.CERT_USER_NAME;
    }

    @Override
    public String getEmail() {
      return ClientAssertionConstants.CERT_USER_EMAIL;
    }
  }

  static class JwtBasedUser implements StandardClaimAccessor {
    private final org.springframework.security.oauth2.jwt.Jwt jwt;

    public JwtBasedUser(final org.springframework.security.oauth2.jwt.Jwt jwt) {
      this.jwt = jwt;
    }

    @Override
    public Map<String, Object> getClaims() {
      return jwt.getClaims();
    }

    @Override
    public String getFullName() {
      return jwt.getClaimAsString("name") != null
          ? jwt.getClaimAsString("name")
          : ClientAssertionConstants.CERT_USER_NAME;
    }

    @Override
    public String getEmail() {
      return jwt.getClaimAsString("email") != null
          ? jwt.getClaimAsString("email")
          : ClientAssertionConstants.CERT_USER_EMAIL;
    }
  }
}
