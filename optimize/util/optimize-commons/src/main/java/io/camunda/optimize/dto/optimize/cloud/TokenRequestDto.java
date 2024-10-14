/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenRequestDto {

  @JsonProperty("client_id")
  private String clientId;

  @JsonProperty("client_secret")
  private String clientSecret;

  @JsonProperty("audience")
  private String audience;

  @JsonProperty("grant_type")
  private String grantType;

  public TokenRequestDto(
      final String clientId,
      final String clientSecret,
      final String audience,
      final String grantType) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.audience = audience;
    this.grantType = grantType;
  }

  public TokenRequestDto() {}

  public String getClientId() {
    return clientId;
  }

  @JsonProperty("client_id")
  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  @JsonProperty("client_secret")
  public void setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getAudience() {
    return audience;
  }

  @JsonProperty("audience")
  public void setAudience(final String audience) {
    this.audience = audience;
  }

  public String getGrantType() {
    return grantType;
  }

  @JsonProperty("grant_type")
  public void setGrantType(final String grantType) {
    this.grantType = grantType;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof TokenRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $clientId = getClientId();
    result = result * PRIME + ($clientId == null ? 43 : $clientId.hashCode());
    final Object $clientSecret = getClientSecret();
    result = result * PRIME + ($clientSecret == null ? 43 : $clientSecret.hashCode());
    final Object $audience = getAudience();
    result = result * PRIME + ($audience == null ? 43 : $audience.hashCode());
    final Object $grantType = getGrantType();
    result = result * PRIME + ($grantType == null ? 43 : $grantType.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TokenRequestDto)) {
      return false;
    }
    final TokenRequestDto other = (TokenRequestDto) o;
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
    final Object this$audience = getAudience();
    final Object other$audience = other.getAudience();
    if (this$audience == null ? other$audience != null : !this$audience.equals(other$audience)) {
      return false;
    }
    final Object this$grantType = getGrantType();
    final Object other$grantType = other.getGrantType();
    if (this$grantType == null
        ? other$grantType != null
        : !this$grantType.equals(other$grantType)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "TokenRequestDto(clientId="
        + getClientId()
        + ", clientSecret="
        + getClientSecret()
        + ", audience="
        + getAudience()
        + ", grantType="
        + getGrantType()
        + ")";
  }

  public static TokenRequestDtoBuilder builder() {
    return new TokenRequestDtoBuilder();
  }

  public static class TokenRequestDtoBuilder {

    private String clientId;
    private String clientSecret;
    private String audience;
    private String grantType;

    TokenRequestDtoBuilder() {}

    @JsonProperty("client_id")
    public TokenRequestDtoBuilder clientId(final String clientId) {
      this.clientId = clientId;
      return this;
    }

    @JsonProperty("client_secret")
    public TokenRequestDtoBuilder clientSecret(final String clientSecret) {
      this.clientSecret = clientSecret;
      return this;
    }

    @JsonProperty("audience")
    public TokenRequestDtoBuilder audience(final String audience) {
      this.audience = audience;
      return this;
    }

    @JsonProperty("grant_type")
    public TokenRequestDtoBuilder grantType(final String grantType) {
      this.grantType = grantType;
      return this;
    }

    public TokenRequestDto build() {
      return new TokenRequestDto(clientId, clientSecret, audience, grantType);
    }

    @Override
    public String toString() {
      return "TokenRequestDto.TokenRequestDtoBuilder(clientId="
          + clientId
          + ", clientSecret="
          + clientSecret
          + ", audience="
          + audience
          + ", grantType="
          + grantType
          + ")";
    }
  }
}
