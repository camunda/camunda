/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OptimizeApiConfiguration {
  @JsonProperty("accessToken")
  private String accessToken;
  @JsonProperty("jwtSetUri")
  private String jwtSetUri;
  @JsonProperty("audience")
  private String audience;
}
