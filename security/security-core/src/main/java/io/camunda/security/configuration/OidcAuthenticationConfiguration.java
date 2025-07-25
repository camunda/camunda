/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.security.auth.OidcGroupsLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class OidcAuthenticationConfiguration {
  public static final String GROUPS_CLAIM_PROPERTY =
      "camunda.security.authentication.oidc.groupsClaim";

  private String issuerUri;
  private String clientId;
  private String clientSecret;
  private String grantType = "authorization_code";
  private String redirectUri;
  private List<String> scope = Arrays.asList("openid", "profile");
  private String jwkSetUri;
  private String authorizationUri;
  private String tokenUri;
  private AuthorizeRequestConfiguration authorizeRequestConfiguration =
      new AuthorizeRequestConfiguration();
  private Set<String> audiences;
  private String usernameClaim = "sub";
  private String clientIdClaim;
  private String groupsClaim;
  private String organizationId;
  // Certificate-based client authentication fields
  private String clientAssertionKeystorePath;
  private String clientAssertionKeystorePassword;
  private String clientAssertionKeystoreKeyAlias;
  private String clientAssertionKeystoreKeyPassword;

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

  public String getGrantType() {
    return grantType;
  }

  public void setGrantType(final String grantType) {
    this.grantType = grantType;
  }

  public String getRedirectUri() {
    return redirectUri;
  }

  public void setRedirectUri(final String redirectUri) {
    this.redirectUri = redirectUri;
  }

  public List<String> getScope() {
    return scope;
  }

  public void setScope(final List<String> scope) {
    this.scope = scope;
  }

  public String getJwkSetUri() {
    return jwkSetUri;
  }

  public void setJwkSetUri(final String jwkSetUri) {
    this.jwkSetUri = jwkSetUri;
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

  public AuthorizeRequestConfiguration getAuthorizeRequest() {
    return authorizeRequestConfiguration;
  }

  public void setAuthorizeRequest(
      final AuthorizeRequestConfiguration authorizeRequestConfiguration) {
    this.authorizeRequestConfiguration = authorizeRequestConfiguration;
  }

  public Set<String> getAudiences() {
    return audiences;
  }

  public void setAudiences(final Set<String> audiences) {
    this.audiences = audiences;
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

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(final String organizationId) {
    this.organizationId = organizationId;
  }

  public String getGroupsClaim() {
    return groupsClaim;
  }

  public void setGroupsClaim(final String groupsClaim) {
    new OidcGroupsLoader(groupsClaim);
    this.groupsClaim = groupsClaim;
  }

  public String getClientAssertionKeystorePath() {
    return clientAssertionKeystorePath;
  }

  public void setClientAssertionKeystorePath(final String clientAssertionKeystorePath) {
    this.clientAssertionKeystorePath = clientAssertionKeystorePath;
  }

  public String getClientAssertionKeystorePassword() {
    return clientAssertionKeystorePassword;
  }

  public void setClientAssertionKeystorePassword(final String clientAssertionKeystorePassword) {
    this.clientAssertionKeystorePassword = clientAssertionKeystorePassword;
  }

  public String getClientAssertionKeystoreKeyAlias() {
    return clientAssertionKeystoreKeyAlias;
  }

  public void setClientAssertionKeystoreKeyAlias(final String clientAssertionKeystoreKeyAlias) {
    this.clientAssertionKeystoreKeyAlias = clientAssertionKeystoreKeyAlias;
  }

  public String getClientAssertionKeystoreKeyPassword() {
    return clientAssertionKeystoreKeyPassword;
  }

  public void setClientAssertionKeystoreKeyPassword(
      final String clientAssertionKeystoreKeyPassword) {
    this.clientAssertionKeystoreKeyPassword = clientAssertionKeystoreKeyPassword;
  }

  public boolean isClientAssertionEnabled() {
    return clientAssertionKeystorePath != null
        && !clientAssertionKeystorePath.isEmpty()
        && clientAssertionKeystorePassword != null
        && !clientAssertionKeystorePassword.isEmpty();
  }
}
