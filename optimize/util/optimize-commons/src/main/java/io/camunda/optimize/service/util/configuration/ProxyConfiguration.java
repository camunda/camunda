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

@JsonIgnoreProperties
public class ProxyConfiguration {

  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("host")
  private String host;

  @JsonProperty("port")
  private Integer port;

  @JsonProperty("sslEnabled")
  private boolean sslEnabled;

  public ProxyConfiguration(
      final boolean enabled, final String host, final Integer port, final boolean sslEnabled) {
    this.enabled = enabled;
    this.host = host;
    this.port = port;
    this.sslEnabled = sslEnabled;
  }

  public ProxyConfiguration() {}

  public void validate() {
    if (enabled) {
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

  public boolean isEnabled() {
    return enabled;
  }

  @JsonProperty("enabled")
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getHost() {
    return host;
  }

  @JsonProperty("host")
  public void setHost(final String host) {
    this.host = host;
  }

  public Integer getPort() {
    return port;
  }

  @JsonProperty("port")
  public void setPort(final Integer port) {
    this.port = port;
  }

  public boolean isSslEnabled() {
    return sslEnabled;
  }

  @JsonProperty("sslEnabled")
  public void setSslEnabled(final boolean sslEnabled) {
    this.sslEnabled = sslEnabled;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProxyConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    final Object $host = getHost();
    result = result * PRIME + ($host == null ? 43 : $host.hashCode());
    final Object $port = getPort();
    result = result * PRIME + ($port == null ? 43 : $port.hashCode());
    result = result * PRIME + (isSslEnabled() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProxyConfiguration)) {
      return false;
    }
    final ProxyConfiguration other = (ProxyConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    final Object this$host = getHost();
    final Object other$host = other.getHost();
    if (this$host == null ? other$host != null : !this$host.equals(other$host)) {
      return false;
    }
    final Object this$port = getPort();
    final Object other$port = other.getPort();
    if (this$port == null ? other$port != null : !this$port.equals(other$port)) {
      return false;
    }
    if (isSslEnabled() != other.isSslEnabled()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ProxyConfiguration(enabled="
        + isEnabled()
        + ", host="
        + getHost()
        + ", port="
        + getPort()
        + ", sslEnabled="
        + isSslEnabled()
        + ")";
  }
}
