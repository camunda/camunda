/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenResponseDto {

  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("token_type")
  private String tokenType;

  @JsonProperty("expires_in")
  private long expiresIn;

  @JsonProperty("scope")
  private String scope;

  public TokenResponseDto() {}

  public String getAccessToken() {
    return accessToken;
  }

  @JsonProperty("access_token")
  public void setAccessToken(final String accessToken) {
    this.accessToken = accessToken;
  }

  public String getTokenType() {
    return tokenType;
  }

  @JsonProperty("token_type")
  public void setTokenType(final String tokenType) {
    this.tokenType = tokenType;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  @JsonProperty("expires_in")
  public void setExpiresIn(final long expiresIn) {
    this.expiresIn = expiresIn;
  }

  public String getScope() {
    return scope;
  }

  @JsonProperty("scope")
  public void setScope(final String scope) {
    this.scope = scope;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof TokenResponseDto;
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
    return "TokenResponseDto(accessToken="
        + getAccessToken()
        + ", tokenType="
        + getTokenType()
        + ", expiresIn="
        + getExpiresIn()
        + ", scope="
        + getScope()
        + ")";
  }
}
