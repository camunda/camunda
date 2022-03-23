/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.cleanup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Period;

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
