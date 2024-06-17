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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties
@Getter
@Builder
public class ProcessDefinitionCleanupConfiguration {

  @JsonProperty("ttl")
  private Period ttl;

  @JsonProperty("cleanupMode")
  private CleanupMode cleanupMode;

  public ProcessDefinitionCleanupConfiguration(Period ttl) {
    this(ttl, null);
  }

  public ProcessDefinitionCleanupConfiguration(CleanupMode cleanupMode) {
    this(null, cleanupMode);
  }
}
