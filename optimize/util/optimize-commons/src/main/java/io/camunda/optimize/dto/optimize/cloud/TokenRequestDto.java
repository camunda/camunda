/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenRequestDto {

  @JsonProperty("client_id")
  private String clientId;

  @JsonProperty("client_secret")
  private String clientSecret;

  @JsonProperty("audience")
  private String audience;

  @JsonProperty("grant_type")
  private String grantType;

  public TokenRequestDto(String clientId, String clientSecret, String audience, String grantType) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.audience = audience;
    this.grantType = grantType;
  }

  public TokenRequestDto() {}
}
