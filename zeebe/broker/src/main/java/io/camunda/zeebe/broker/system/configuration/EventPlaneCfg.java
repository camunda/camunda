/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * The Event Plane the physical tenant registers in, from {@code camunda.event-plane}. Without an
 * endpoint, the tenant doesn't register.
 */
public final class EventPlaneCfg {

  private @Nullable URI endpoint;
  private @Nullable String username;
  private @Nullable String password;
  private @Nullable String clientId;
  private @Nullable String clientSecret;
  private @Nullable URI tokenUrl;
  private @Nullable String audience;
  private @Nullable String scope;
  private @Nullable String resource;

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

  // Safeguard, as for the license key: kept out of the broker configuration's JSON.
  @JsonIgnore
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

  @JsonIgnore
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

  public @Nullable String getScope() {
    return scope;
  }

  public void setScope(final @Nullable String scope) {
    this.scope = scope;
  }

  public @Nullable String getResource() {
    return resource;
  }

  public void setResource(final @Nullable String resource) {
    this.resource = resource;
  }

  @Override
  public String toString() {
    return "EventPlaneCfg{endpoint="
        + endpoint
        + ", username="
        + username
        + ", password=***, clientId="
        + clientId
        + ", clientSecret=***, tokenUrl="
        + tokenUrl
        + ", audience="
        + audience
        + ", scope="
        + scope
        + ", resource="
        + resource
        + "}";
  }
}
