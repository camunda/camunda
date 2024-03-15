/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.properties.common;

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

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

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
