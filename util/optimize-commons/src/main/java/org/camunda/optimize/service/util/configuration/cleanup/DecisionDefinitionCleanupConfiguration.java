/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.cleanup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Period;

@JsonIgnoreProperties(ignoreUnknown = false)
public class DecisionDefinitionCleanupConfiguration {
  @JsonProperty("ttl")
  private Period ttl;

  protected DecisionDefinitionCleanupConfiguration() {
  }

  public DecisionDefinitionCleanupConfiguration(Period ttl) {
    this.ttl = ttl;

  }

  public Period getTtl() {
    return ttl;
  }

}
