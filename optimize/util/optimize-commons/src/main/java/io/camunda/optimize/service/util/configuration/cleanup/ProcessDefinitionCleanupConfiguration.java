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
public class ProcessDefinitionCleanupConfiguration {

  @JsonProperty("ttl")
  private Period ttl;

  @JsonProperty("cleanupMode")
  private CleanupMode cleanupMode;

  public ProcessDefinitionCleanupConfiguration(final Period ttl) {
    this(ttl, null);
  }

  public ProcessDefinitionCleanupConfiguration(final CleanupMode cleanupMode) {
    this(null, cleanupMode);
  }

  public ProcessDefinitionCleanupConfiguration(final Period ttl, final CleanupMode cleanupMode) {
    this.ttl = ttl;
    this.cleanupMode = cleanupMode;
  }

  public ProcessDefinitionCleanupConfiguration() {}

  public Period getTtl() {
    return ttl;
  }

  public CleanupMode getCleanupMode() {
    return cleanupMode;
  }

  public static ProcessDefinitionCleanupConfigurationBuilder builder() {
    return new ProcessDefinitionCleanupConfigurationBuilder();
  }

  public static class ProcessDefinitionCleanupConfigurationBuilder {

    private Period ttl;
    private CleanupMode cleanupMode;

    ProcessDefinitionCleanupConfigurationBuilder() {}

    @JsonProperty("ttl")
    public ProcessDefinitionCleanupConfigurationBuilder ttl(final Period ttl) {
      this.ttl = ttl;
      return this;
    }

    @JsonProperty("cleanupMode")
    public ProcessDefinitionCleanupConfigurationBuilder cleanupMode(final CleanupMode cleanupMode) {
      this.cleanupMode = cleanupMode;
      return this;
    }

    public ProcessDefinitionCleanupConfiguration build() {
      return new ProcessDefinitionCleanupConfiguration(ttl, cleanupMode);
    }

    @Override
    public String toString() {
      return "ProcessDefinitionCleanupConfiguration.ProcessDefinitionCleanupConfigurationBuilder(ttl="
          + ttl
          + ", cleanupMode="
          + cleanupMode
          + ")";
    }
  }
}
