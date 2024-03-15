/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.common.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenRequest {

  private String grantType;
  private String audience;
  private String clientId;
  private String clientSecret;

  TokenRequest(final String audience, final String clientId, final String clientSecret) {
    grantType = "client_credentials";
    this.audience = audience;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  @JsonProperty("grant_type")
  public String getGrantType() {
    return grantType;
  }

  public void setGrantType(final String grantType) {
    this.grantType = grantType;
  }

  @JsonProperty("audience")
  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
  }

  @JsonProperty("client_id")
  public String getClientId() {
    return clientId;
  }

  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  @JsonProperty("client_secret")
  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
  }
}
