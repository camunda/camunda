/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.cleanup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Period;

@JsonIgnoreProperties
public class DecisionDefinitionCleanupConfiguration {

  @JsonProperty("ttl")
  private Period ttl;

  public DecisionDefinitionCleanupConfiguration(final Period ttl) {
    this.ttl = ttl;
  }

  public DecisionDefinitionCleanupConfiguration() {}

  public Period getTtl() {
    return ttl;
  }
}
