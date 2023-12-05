/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.property;

public class IdentityProperties {
  public static final String ALL_RESOURCES = "*";
  public static final String FULL_GROUP_ACCESS = "";
  private String issuerUrl;
  private String baseUrl;
  private String issuerBackendUrl;
  private String redirectRootUrl;
  private String clientId;
  private String clientSecret;
  private String audience;
  private boolean resourcePermissionsEnabled = false;
  private boolean userAccessRestrictionsEnabled = true;

  public String getIssuerUrl() {
    return issuerUrl;
  }

  public void setIssuerUrl(final String issuerUrl) {
    this.issuerUrl = issuerUrl;
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

  public String getIssuerBackendUrl() {
    return issuerBackendUrl;
  }

  public void setIssuerBackendUrl(final String issuerBackendUrl) {
    this.issuerBackendUrl = issuerBackendUrl;
  }

  public String getRedirectRootUrl() {
    return redirectRootUrl;
  }

  public void setRedirectRootUrl(String redirectRootUrl) {
    this.redirectRootUrl = redirectRootUrl;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public boolean isUserAccessRestrictionsEnabled() {
    return userAccessRestrictionsEnabled;
  }

  public IdentityProperties setUserAccessRestrictionsEnabled(
      boolean userAccessRestrictionsEnabled) {
    this.userAccessRestrictionsEnabled = userAccessRestrictionsEnabled;
    return this;
  }

  public IdentityProperties setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
    return this;
  }

  public boolean isResourcePermissionsEnabled() {
    return resourcePermissionsEnabled;
  }

  public IdentityProperties setResourcePermissionsEnabled(boolean resourcePermissionsEnabled) {
    this.resourcePermissionsEnabled = resourcePermissionsEnabled;
    return this;
  }
}
