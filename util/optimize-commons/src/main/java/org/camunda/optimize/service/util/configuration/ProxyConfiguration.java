/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTIC_SEARCH_PROXY;

@JsonIgnoreProperties(ignoreUnknown = false)
public class ProxyConfiguration {
  @JsonProperty("enabled")
  private boolean enabled;
  @JsonProperty("host")
  private String host;
  @JsonProperty("port")
  private Integer port;
  @JsonProperty("sslEnabled")
  private boolean sslEnabled;

  public ProxyConfiguration() {
  }

  public ProxyConfiguration(final boolean enabled, final String host, final Integer port, final boolean sslEnabled) {
    this.enabled = enabled;
    this.host = host;
    this.port = port;
    this.sslEnabled = sslEnabled;
  }

  public void validate() {
    if (this.enabled) {
      if (host == null || host.isEmpty()) {
        throw new OptimizeConfigurationException(
          ELASTIC_SEARCH_PROXY + ".host must be set and not empty if proxy is enabled"
        );
      }
      if (port == null) {
        throw new OptimizeConfigurationException(
          ELASTIC_SEARCH_PROXY + ".port must be set and not empty if proxy is enabled"
        );
      }
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(final Integer port) {
    this.port = port;
  }

  public boolean isSslEnabled() {
    return sslEnabled;
  }

  public void setSslEnabled(final boolean sslEnabled) {
    this.sslEnabled = sslEnabled;
  }
}
