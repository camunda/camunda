/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.config;

public class OAuthConfig {

  private String clientId;
  private String clientSecret;
  private String audience;
  private String scope;
  private String resource;
  private String authorizationServerUrl;
  private Long connectTimeoutMs;
  private Long readTimeoutMs;

  public String getClientId() {
    return clientId;
  }

  public OAuthConfig setClientId(final String clientId) {
    this.clientId = clientId;
    return this;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public OAuthConfig setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
    return this;
  }

  public String getAudience() {
    return audience;
  }

  public OAuthConfig setAudience(final String audience) {
    this.audience = audience;
    return this;
  }

  public String getScope() {
    return scope;
  }

  public OAuthConfig setScope(final String scope) {
    this.scope = scope;
    return this;
  }

  public String getResource() {
    return resource;
  }

  public OAuthConfig setResource(final String resource) {
    this.resource = resource;
    return this;
  }

  public String getAuthorizationServerUrl() {
    return authorizationServerUrl;
  }

  public OAuthConfig setAuthorizationServerUrl(final String authorizationServerUrl) {
    this.authorizationServerUrl = authorizationServerUrl;
    return this;
  }

  public Long getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public OAuthConfig setConnectTimeoutMs(final Long connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
    return this;
  }

  public Long getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public OAuthConfig setReadTimeoutMs(final Long readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
    return this;
  }
}
