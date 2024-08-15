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
    final int PRIME = 59;
    int result = 1;
    final Object $accessToken = getAccessToken();
    result = result * PRIME + ($accessToken == null ? 43 : $accessToken.hashCode());
    final Object $tokenType = getTokenType();
    result = result * PRIME + ($tokenType == null ? 43 : $tokenType.hashCode());
    final long $expiresIn = getExpiresIn();
    result = result * PRIME + (int) ($expiresIn >>> 32 ^ $expiresIn);
    final Object $scope = getScope();
    result = result * PRIME + ($scope == null ? 43 : $scope.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TokenResponseDto)) {
      return false;
    }
    final TokenResponseDto other = (TokenResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$accessToken = getAccessToken();
    final Object other$accessToken = other.getAccessToken();
    if (this$accessToken == null
        ? other$accessToken != null
        : !this$accessToken.equals(other$accessToken)) {
      return false;
    }
    final Object this$tokenType = getTokenType();
    final Object other$tokenType = other.getTokenType();
    if (this$tokenType == null
        ? other$tokenType != null
        : !this$tokenType.equals(other$tokenType)) {
      return false;
    }
    if (getExpiresIn() != other.getExpiresIn()) {
      return false;
    }
    final Object this$scope = getScope();
    final Object other$scope = other.getScope();
    if (this$scope == null ? other$scope != null : !this$scope.equals(other$scope)) {
      return false;
    }
    return true;
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
