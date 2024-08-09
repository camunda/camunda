/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;

@Data
public class AuthConfiguration {
  @JsonProperty("cloud")
  private CloudAuthConfiguration cloudAuthConfiguration;

  @JsonProperty("ccsm")
  private CCSMAuthConfiguration ccsmAuthConfiguration;

  @JsonProperty("token.lifeMin")
  private Integer tokenLifeTime;

  @JsonProperty("token.secret")
  private String tokenSecret;

  @JsonProperty("cookie")
  private CookieConfiguration cookieConfiguration;

  @JsonProperty("superUserIds")
  private List<String> superUserIds;

  @JsonProperty("superGroupIds")
  private List<String> superGroupIds;

  @JsonIgnore
  public int getTokenLifeTimeMinutes() {
    return tokenLifeTime;
  }

  public Optional<String> getTokenSecret() {
    return Optional.ofNullable(tokenSecret);
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  @JsonProperty("token")
  private void unpackToken(final Map<String, String> token) {
    this.tokenLifeTime = Integer.valueOf(token.get("lifeMin"));
    this.tokenSecret = token.get("secret");
  }
}
