/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.config;

import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.config.OidcConfig;
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Maps Spring Boot configuration properties to gatekeeper domain config records. */
@ConfigurationProperties(prefix = "camunda.security")
public class GatekeeperProperties {

  private AuthenticationProperties authentication = new AuthenticationProperties();

  public AuthenticationProperties getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final AuthenticationProperties authentication) {
    this.authentication = authentication;
  }

  /** Converts the mutable authentication properties to an immutable domain config record. */
  public AuthenticationConfig toAuthenticationConfig() {
    return new AuthenticationConfig(
        AuthenticationMethod.parse(authentication.getMethod()).orElse(AuthenticationMethod.BASIC),
        Duration.parse(authentication.getAuthenticationRefreshInterval()),
        authentication.isUnprotectedApi(),
        authentication.getOidc().toOidcConfig());
  }

  /** Authentication properties matching {@code camunda.security.authentication.*}. */
  public static class AuthenticationProperties {

    private String method = "basic";
    private String authenticationRefreshInterval = "PT30S";
    private boolean unprotectedApi = false;
    private OidcProperties oidc = new OidcProperties();

    public String getMethod() {
      return method;
    }

    public void setMethod(final String method) {
      this.method = method;
    }

    public String getAuthenticationRefreshInterval() {
      return authenticationRefreshInterval;
    }

    public void setAuthenticationRefreshInterval(final String authenticationRefreshInterval) {
      this.authenticationRefreshInterval = authenticationRefreshInterval;
    }

    public boolean isUnprotectedApi() {
      return unprotectedApi;
    }

    public void setUnprotectedApi(final boolean unprotectedApi) {
      this.unprotectedApi = unprotectedApi;
    }

    public OidcProperties getOidc() {
      return oidc;
    }

    public void setOidc(final OidcProperties oidc) {
      this.oidc = oidc;
    }
  }

  /** OIDC properties matching {@code camunda.security.authentication.oidc.*}. */
  public static class OidcProperties {

    private String issuerUri;
    private String clientId;
    private String clientSecret;
    private String jwkSetUri;
    private List<String> additionalJwkSetUris = new ArrayList<>();
    private String authorizationUri;
    private String tokenUri;
    private String endSessionEndpointUri;
    private String usernameClaim = "sub";
    private String clientIdClaim;
    private String groupsClaim;
    private boolean preferUsernameClaim = false;
    private String scope;
    private List<String> audiences = new ArrayList<>();
    private String redirectUri;
    private String clockSkew = "PT60S";
    private boolean idpLogoutEnabled = true;
    private String grantType = "authorization_code";
    private String clientAuthenticationMethod = "client_secret_basic";
    private String registrationId;

    /** Converts the mutable OIDC properties to an immutable domain config record. */
    public OidcConfig toOidcConfig() {
      return new OidcConfig(
          issuerUri,
          clientId,
          clientSecret,
          jwkSetUri,
          additionalJwkSetUris,
          authorizationUri,
          tokenUri,
          endSessionEndpointUri,
          usernameClaim,
          clientIdClaim,
          groupsClaim,
          preferUsernameClaim,
          scope,
          audiences,
          redirectUri,
          Duration.parse(clockSkew),
          idpLogoutEnabled,
          grantType,
          clientAuthenticationMethod,
          registrationId);
    }

    public String getIssuerUri() {
      return issuerUri;
    }

    public void setIssuerUri(final String issuerUri) {
      this.issuerUri = issuerUri;
    }

    public String getClientId() {
      return clientId;
    }

    public void setClientId(final String clientId) {
      this.clientId = clientId;
    }

    public String getClientSecret() {
      return clientSecret;
    }

    public void setClientSecret(final String clientSecret) {
      this.clientSecret = clientSecret;
    }

    public String getJwkSetUri() {
      return jwkSetUri;
    }

    public void setJwkSetUri(final String jwkSetUri) {
      this.jwkSetUri = jwkSetUri;
    }

    public List<String> getAdditionalJwkSetUris() {
      return additionalJwkSetUris;
    }

    public void setAdditionalJwkSetUris(final List<String> additionalJwkSetUris) {
      this.additionalJwkSetUris = additionalJwkSetUris;
    }

    public String getAuthorizationUri() {
      return authorizationUri;
    }

    public void setAuthorizationUri(final String authorizationUri) {
      this.authorizationUri = authorizationUri;
    }

    public String getTokenUri() {
      return tokenUri;
    }

    public void setTokenUri(final String tokenUri) {
      this.tokenUri = tokenUri;
    }

    public String getEndSessionEndpointUri() {
      return endSessionEndpointUri;
    }

    public void setEndSessionEndpointUri(final String endSessionEndpointUri) {
      this.endSessionEndpointUri = endSessionEndpointUri;
    }

    public String getUsernameClaim() {
      return usernameClaim;
    }

    public void setUsernameClaim(final String usernameClaim) {
      this.usernameClaim = usernameClaim;
    }

    public String getClientIdClaim() {
      return clientIdClaim;
    }

    public void setClientIdClaim(final String clientIdClaim) {
      this.clientIdClaim = clientIdClaim;
    }

    public String getGroupsClaim() {
      return groupsClaim;
    }

    public void setGroupsClaim(final String groupsClaim) {
      this.groupsClaim = groupsClaim;
    }

    public boolean isPreferUsernameClaim() {
      return preferUsernameClaim;
    }

    public void setPreferUsernameClaim(final boolean preferUsernameClaim) {
      this.preferUsernameClaim = preferUsernameClaim;
    }

    public String getScope() {
      return scope;
    }

    public void setScope(final String scope) {
      this.scope = scope;
    }

    public List<String> getAudiences() {
      return audiences;
    }

    public void setAudiences(final List<String> audiences) {
      this.audiences = audiences;
    }

    public String getRedirectUri() {
      return redirectUri;
    }

    public void setRedirectUri(final String redirectUri) {
      this.redirectUri = redirectUri;
    }

    public String getClockSkew() {
      return clockSkew;
    }

    public void setClockSkew(final String clockSkew) {
      this.clockSkew = clockSkew;
    }

    public boolean isIdpLogoutEnabled() {
      return idpLogoutEnabled;
    }

    public void setIdpLogoutEnabled(final boolean idpLogoutEnabled) {
      this.idpLogoutEnabled = idpLogoutEnabled;
    }

    public String getGrantType() {
      return grantType;
    }

    public void setGrantType(final String grantType) {
      this.grantType = grantType;
    }

    public String getClientAuthenticationMethod() {
      return clientAuthenticationMethod;
    }

    public void setClientAuthenticationMethod(final String clientAuthenticationMethod) {
      this.clientAuthenticationMethod = clientAuthenticationMethod;
    }

    public String getRegistrationId() {
      return registrationId;
    }

    public void setRegistrationId(final String registrationId) {
      this.registrationId = registrationId;
    }
  }
}
