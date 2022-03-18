/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
}
