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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
