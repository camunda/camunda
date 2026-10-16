/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * The Event Plane each physical tenant registers in, which keeps a copy of its log. Without an
 * endpoint, the tenant doesn't register. A tenant can name its own Event Plane under {@code
 * camunda.physical-tenants.<id>.event-plane}.
 *
 * <p>The broker authenticates with OAuth client credentials if a client id is set, with basic
 * authentication if a username is set, and not at all otherwise.
 */
public class EventPlane {

  /** Where the Event Plane's API is, such as {@code http://event-plane:8080}. */
  private @Nullable URI endpoint;

  /** The user for basic authentication, for local development. */
  private @Nullable String username;

  /** The password for basic authentication. */
  private @Nullable String password;

  /** The OAuth client the broker gets its tokens for. */
  private @Nullable String clientId;

  /** The OAuth client's secret. */
  private @Nullable String clientSecret;

  /** The identity provider's token endpoint. */
  private @Nullable URI tokenUrl;

  /** The audience the tokens are for, if the identity provider needs one. */
  private @Nullable String audience;

  public @Nullable URI getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(final @Nullable URI endpoint) {
    this.endpoint = endpoint;
  }

  public @Nullable String getUsername() {
    return username;
  }

  public void setUsername(final @Nullable String username) {
    this.username = username;
  }

  public @Nullable String getPassword() {
    return password;
  }

  public void setPassword(final @Nullable String password) {
    this.password = password;
  }

  public @Nullable String getClientId() {
    return clientId;
  }

  public void setClientId(final @Nullable String clientId) {
    this.clientId = clientId;
  }

  public @Nullable String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(final @Nullable String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public @Nullable URI getTokenUrl() {
    return tokenUrl;
  }

  public void setTokenUrl(final @Nullable URI tokenUrl) {
    this.tokenUrl = tokenUrl;
  }

  public @Nullable String getAudience() {
    return audience;
  }

  public void setAudience(final @Nullable String audience) {
    this.audience = audience;
  }

  @Override
  public String toString() {
    return "EventPlane{endpoint="
        + endpoint
        + ", username="
        + username
        + ", password=***, clientId="
        + clientId
        + ", clientSecret=***, tokenUrl="
        + tokenUrl
        + ", audience="
        + audience
        + "}";
  }
}
