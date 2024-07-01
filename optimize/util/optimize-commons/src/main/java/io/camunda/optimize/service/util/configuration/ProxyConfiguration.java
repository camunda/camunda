/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_PROXY;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties
@Data
public class ProxyConfiguration {
  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("host")
  private String host;

  @JsonProperty("port")
  private Integer port;

  @JsonProperty("sslEnabled")
  private boolean sslEnabled;

  public void validate() {
    if (this.enabled) {
      if (host == null || host.isEmpty()) {
        throw new OptimizeConfigurationException(
            ELASTICSEARCH_PROXY + ".host must be set and not empty if proxy is enabled");
      }
      if (port == null) {
        throw new OptimizeConfigurationException(
            ELASTICSEARCH_PROXY + ".port must be set and not empty if proxy is enabled");
      }
    }
  }
}
