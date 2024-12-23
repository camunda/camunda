/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.properties.common;

import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

@Deprecated(forRemoval = true, since = "8.6")
public class Client {

  private String clientId;
  private String clientSecret;
  private String username;
  private String password;
  private Boolean enabled = false;
  private String url;
  private String authUrl;
  private String baseUrl;

  @Override
  public String toString() {
    return "Client{"
        + "clientId='"
        + "***"
        + '\''
        + ", clientSecret='"
        + "***"
        + '\''
        + ", username='"
        + "***"
        + '\''
        + ", password='"
        + "***"
        + '\''
        + ", enabled="
        + enabled
        + ", url='"
        + url
        + '\''
        + ", authUrl='"
        + authUrl
        + '\''
        + ", baseUrl='"
        + baseUrl
        + '\''
        + '}';
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.client-id")
  public String getClientId() {
    return clientId;
  }

  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.client-secret")
  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.username")
  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.password")
  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.issuer")
  public String getAuthUrl() {
    return authUrl;
  }

  public void setAuthUrl(final String authUrl) {
    this.authUrl = authUrl;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
  }
}
