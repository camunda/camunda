/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import java.util.Objects;

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

  @JsonProperty("username")
  private String username;

  @JsonProperty("password")
  private String password;

  public ProxyConfiguration(
      final boolean enabled,
      final String host,
      final Integer port,
      final boolean sslEnabled,
      final String username,
      final String password) {
    this.enabled = enabled;
    this.host = host;
    this.port = port;
    this.sslEnabled = sslEnabled;
    this.username = username;
    this.password = password;
  }

  public ProxyConfiguration(
      final boolean enabled, final String host, final Integer port, final boolean sslEnabled) {
    this(enabled, host, port, sslEnabled, null, null);
  }

  public ProxyConfiguration() {}

  public void validate() {
    validate("proxy");
  }

  public void validate(final String configPath) {
    if (enabled) {
      if (host == null || host.isEmpty()) {
        throw new OptimizeConfigurationException(
            configPath + ".host must be set and not empty if proxy is enabled");
      }
      if (port == null) {
        throw new OptimizeConfigurationException(
            configPath + ".port must be set and not empty if proxy is enabled");
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

  public String getUsername() {
    return username;
  }

  @JsonProperty("username")
  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  @JsonProperty("password")
  public void setPassword(final String password) {
    this.password = password;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProxyConfiguration;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProxyConfiguration that = (ProxyConfiguration) o;
    return enabled == that.enabled
        && sslEnabled == that.sslEnabled
        && Objects.equals(host, that.host)
        && Objects.equals(port, that.port)
        && Objects.equals(username, that.username)
        && Objects.equals(password, that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, host, port, sslEnabled, username, password);
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
        + ", username="
        + getUsername()
        + ")";
  }
}
