/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.camunda.optimize.util.SuppressionConstants;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
