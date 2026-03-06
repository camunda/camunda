/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class OidcAuthenticationConfiguration {

  public static final Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(60);

  private String issuerUri;
  private String clientName;
  private String clientId;
  private String clientSecret;
  private String idTokenAlgorithm = "RS256";
  private String grantType = "authorization_code";
  private String redirectUri;
  private List<String> scope = Arrays.asList("openid", "profile");
  private String jwkSetUri;
  private List<String> additionalJwkSetUris;
  private String authorizationUri;
  private String endSessionEndpointUri;
  private String tokenUri;
  private Set<String> audiences;
  private String usernameClaim = "sub";
  private String clientIdClaim;
  private String groupsClaim;
  private boolean preferUsernameClaim;
  private String organizationId;
  private List<String> resource;
  private String clientAuthenticationMethod = "client_secret_basic";
  private Duration clockSkew = DEFAULT_CLOCK_SKEW;
  private boolean idpLogoutEnabled = true;
  private boolean userInfoEnabled = true;

  // ALL getters and setters for each field
  public String getIssuerUri() { return issuerUri; }
  public void setIssuerUri(final String issuerUri) { this.issuerUri = issuerUri; }
  public String getClientName() { return clientName; }
  public void setClientName(final String clientName) { this.clientName = clientName; }
  public String getClientId() { return clientId; }
  public void setClientId(final String clientId) { this.clientId = clientId; }
  public String getClientSecret() { return clientSecret; }
  public void setClientSecret(final String clientSecret) { this.clientSecret = clientSecret; }
  public String getIdTokenAlgorithm() { return idTokenAlgorithm; }
  public void setIdTokenAlgorithm(final String idTokenAlgorithm) { this.idTokenAlgorithm = idTokenAlgorithm; }
  public String getGrantType() { return grantType; }
  public void setGrantType(final String grantType) { this.grantType = grantType; }
  public String getRedirectUri() { return redirectUri; }
  public void setRedirectUri(final String redirectUri) { this.redirectUri = redirectUri; }
  public List<String> getScope() { return scope; }
  public void setScope(final List<String> scope) { this.scope = scope; }
  public String getJwkSetUri() { return jwkSetUri; }
  public void setJwkSetUri(final String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
  public List<String> getAdditionalJwkSetUris() { return additionalJwkSetUris; }
  public void setAdditionalJwkSetUris(final List<String> additionalJwkSetUris) { this.additionalJwkSetUris = additionalJwkSetUris; }
  public String getAuthorizationUri() { return authorizationUri; }
  public void setAuthorizationUri(final String authorizationUri) { this.authorizationUri = authorizationUri; }
  public String getEndSessionEndpointUri() { return endSessionEndpointUri; }
  public void setEndSessionEndpointUri(final String endSessionEndpointUri) { this.endSessionEndpointUri = endSessionEndpointUri; }
  public String getTokenUri() { return tokenUri; }
  public void setTokenUri(final String tokenUri) { this.tokenUri = tokenUri; }
  public Set<String> getAudiences() { return audiences; }
  public void setAudiences(final Set<String> audiences) { this.audiences = audiences; }
  public String getUsernameClaim() { return usernameClaim; }
  public void setUsernameClaim(final String usernameClaim) { this.usernameClaim = usernameClaim; }
  public String getClientIdClaim() { return clientIdClaim; }
  public void setClientIdClaim(final String clientIdClaim) { this.clientIdClaim = clientIdClaim; }
  public String getGroupsClaim() { return groupsClaim; }
  public void setGroupsClaim(final String groupsClaim) { this.groupsClaim = groupsClaim; }
  public boolean isGroupsClaimConfigured() { return groupsClaim != null && !groupsClaim.isBlank(); }
  public boolean isPreferUsernameClaim() { return preferUsernameClaim; }
  public void setPreferUsernameClaim(final boolean preferUsernameClaim) { this.preferUsernameClaim = preferUsernameClaim; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(final String organizationId) { this.organizationId = organizationId; }
  public List<String> getResource() { return resource; }
  public void setResource(final List<String> resource) { this.resource = resource; }
  public String getClientAuthenticationMethod() { return clientAuthenticationMethod; }
  public void setClientAuthenticationMethod(final String clientAuthenticationMethod) { this.clientAuthenticationMethod = clientAuthenticationMethod; }
  public Duration getClockSkew() { return clockSkew; }
  public void setClockSkew(final Duration clockSkew) { this.clockSkew = clockSkew; }
  public boolean isIdpLogoutEnabled() { return idpLogoutEnabled; }
  public void setIdpLogoutEnabled(final boolean idpLogoutEnabled) { this.idpLogoutEnabled = idpLogoutEnabled; }
  public boolean isUserInfoEnabled() { return userInfoEnabled; }
  public void setUserInfoEnabled(final boolean userInfoEnabled) { this.userInfoEnabled = userInfoEnabled; }

  public boolean isSet() {
    return issuerUri != null || clientId != null || clientName != null || clientSecret != null;
  }
}
