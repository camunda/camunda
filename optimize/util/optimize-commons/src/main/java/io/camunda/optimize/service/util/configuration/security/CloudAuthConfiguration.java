/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.security;

import java.util.Optional;

public class CloudAuthConfiguration {

  // oauth client id to use by Optimize
  private String clientId;
  // oauth client secret to use by Optimize
  private String clientSecret;
  // the Auth0 tenant domain
  private String domain;
  // the Auth0 custom domain used on a tenant (usually used for the login page)
  private String customDomain;
  // the OpenIdConnect attribute where the user id is stored
  private String userIdAttributeName;
  // the name of the claim where the special organizations property is stored
  private String organizationClaimName;
  // the organization id this Optimize instance belongs to
  private String organizationId;
  // the id of the cluster Optimize belongs to
  private String clusterId;
  // the audience (scope) for API access
  private String audience;
  // the audience requested for a users service access token
  private String userAccessTokenAudience;
  // URL to request access tokens
  private String tokenUrl;

  public CloudAuthConfiguration() {}

  public Optional<String> getUserAccessTokenAudience() {
    return Optional.ofNullable(userAccessTokenAudience);
  }

  public void setUserAccessTokenAudience(final String userAccessTokenAudience) {
    this.userAccessTokenAudience = userAccessTokenAudience;
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

  public String getDomain() {
    return domain;
  }

  public void setDomain(final String domain) {
    this.domain = domain;
  }

  public String getCustomDomain() {
    return customDomain;
  }

  public void setCustomDomain(final String customDomain) {
    this.customDomain = customDomain;
  }

  public String getUserIdAttributeName() {
    return userIdAttributeName;
  }

  public void setUserIdAttributeName(final String userIdAttributeName) {
    this.userIdAttributeName = userIdAttributeName;
  }

  public String getOrganizationClaimName() {
    return organizationClaimName;
  }

  public void setOrganizationClaimName(final String organizationClaimName) {
    this.organizationClaimName = organizationClaimName;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(final String organizationId) {
    this.organizationId = organizationId;
  }

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
  }

  public String getTokenUrl() {
    return tokenUrl;
  }

  public void setTokenUrl(final String tokenUrl) {
    this.tokenUrl = tokenUrl;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CloudAuthConfiguration;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "CloudAuthConfiguration(clientId="
        + getClientId()
        + ", clientSecret="
        + getClientSecret()
        + ", domain="
        + getDomain()
        + ", customDomain="
        + getCustomDomain()
        + ", userIdAttributeName="
        + getUserIdAttributeName()
        + ", organizationClaimName="
        + getOrganizationClaimName()
        + ", organizationId="
        + getOrganizationId()
        + ", clusterId="
        + getClusterId()
        + ", audience="
        + getAudience()
        + ", userAccessTokenAudience="
        + getUserAccessTokenAudience()
        + ", tokenUrl="
        + getTokenUrl()
        + ")";
  }
}
