/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.cleanup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Period;

@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties
@Getter
public class ProcessDefinitionCleanupConfiguration {

  @JsonProperty("ttl")
  private Period ttl;
  @JsonProperty("processDataCleanupMode")
  private CleanupMode processDataCleanupMode;

  public ProcessDefinitionCleanupConfiguration(Period ttl) {
    this(ttl, null);
  }

  public ProcessDefinitionCleanupConfiguration(CleanupMode processDataCleanupMode) {
    this(null, processDataCleanupMode);
  }

}
