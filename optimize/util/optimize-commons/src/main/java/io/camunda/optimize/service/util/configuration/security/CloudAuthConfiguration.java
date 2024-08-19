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
    final int PRIME = 59;
    int result = 1;
    final Object $clientId = getClientId();
    result = result * PRIME + ($clientId == null ? 43 : $clientId.hashCode());
    final Object $clientSecret = getClientSecret();
    result = result * PRIME + ($clientSecret == null ? 43 : $clientSecret.hashCode());
    final Object $domain = getDomain();
    result = result * PRIME + ($domain == null ? 43 : $domain.hashCode());
    final Object $customDomain = getCustomDomain();
    result = result * PRIME + ($customDomain == null ? 43 : $customDomain.hashCode());
    final Object $userIdAttributeName = getUserIdAttributeName();
    result = result * PRIME + ($userIdAttributeName == null ? 43 : $userIdAttributeName.hashCode());
    final Object $organizationClaimName = getOrganizationClaimName();
    result =
        result * PRIME + ($organizationClaimName == null ? 43 : $organizationClaimName.hashCode());
    final Object $organizationId = getOrganizationId();
    result = result * PRIME + ($organizationId == null ? 43 : $organizationId.hashCode());
    final Object $clusterId = getClusterId();
    result = result * PRIME + ($clusterId == null ? 43 : $clusterId.hashCode());
    final Object $audience = getAudience();
    result = result * PRIME + ($audience == null ? 43 : $audience.hashCode());
    final Object $userAccessTokenAudience = getUserAccessTokenAudience();
    result =
        result * PRIME
            + ($userAccessTokenAudience == null ? 43 : $userAccessTokenAudience.hashCode());
    final Object $tokenUrl = getTokenUrl();
    result = result * PRIME + ($tokenUrl == null ? 43 : $tokenUrl.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CloudAuthConfiguration)) {
      return false;
    }
    final CloudAuthConfiguration other = (CloudAuthConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$clientId = getClientId();
    final Object other$clientId = other.getClientId();
    if (this$clientId == null ? other$clientId != null : !this$clientId.equals(other$clientId)) {
      return false;
    }
    final Object this$clientSecret = getClientSecret();
    final Object other$clientSecret = other.getClientSecret();
    if (this$clientSecret == null
        ? other$clientSecret != null
        : !this$clientSecret.equals(other$clientSecret)) {
      return false;
    }
    final Object this$domain = getDomain();
    final Object other$domain = other.getDomain();
    if (this$domain == null ? other$domain != null : !this$domain.equals(other$domain)) {
      return false;
    }
    final Object this$customDomain = getCustomDomain();
    final Object other$customDomain = other.getCustomDomain();
    if (this$customDomain == null
        ? other$customDomain != null
        : !this$customDomain.equals(other$customDomain)) {
      return false;
    }
    final Object this$userIdAttributeName = getUserIdAttributeName();
    final Object other$userIdAttributeName = other.getUserIdAttributeName();
    if (this$userIdAttributeName == null
        ? other$userIdAttributeName != null
        : !this$userIdAttributeName.equals(other$userIdAttributeName)) {
      return false;
    }
    final Object this$organizationClaimName = getOrganizationClaimName();
    final Object other$organizationClaimName = other.getOrganizationClaimName();
    if (this$organizationClaimName == null
        ? other$organizationClaimName != null
        : !this$organizationClaimName.equals(other$organizationClaimName)) {
      return false;
    }
    final Object this$organizationId = getOrganizationId();
    final Object other$organizationId = other.getOrganizationId();
    if (this$organizationId == null
        ? other$organizationId != null
        : !this$organizationId.equals(other$organizationId)) {
      return false;
    }
    final Object this$clusterId = getClusterId();
    final Object other$clusterId = other.getClusterId();
    if (this$clusterId == null
        ? other$clusterId != null
        : !this$clusterId.equals(other$clusterId)) {
      return false;
    }
    final Object this$audience = getAudience();
    final Object other$audience = other.getAudience();
    if (this$audience == null ? other$audience != null : !this$audience.equals(other$audience)) {
      return false;
    }
    final Object this$userAccessTokenAudience = getUserAccessTokenAudience();
    final Object other$userAccessTokenAudience = other.getUserAccessTokenAudience();
    if (this$userAccessTokenAudience == null
        ? other$userAccessTokenAudience != null
        : !this$userAccessTokenAudience.equals(other$userAccessTokenAudience)) {
      return false;
    }
    final Object this$tokenUrl = getTokenUrl();
    final Object other$tokenUrl = other.getTokenUrl();
    if (this$tokenUrl == null ? other$tokenUrl != null : !this$tokenUrl.equals(other$tokenUrl)) {
      return false;
    }
    return true;
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
