/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.configuration;

import java.util.Objects;

public class ProxyConfiguration {

  private boolean enabled;
  private String host;
  private Integer port;
  private boolean sslEnabled;
  private String username;
  private String password;

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

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
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
    return "ProxyConfiguration{"
        + "enabled="
        + enabled
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + ", sslEnabled="
        + sslEnabled
        + ", username='"
        + username
        + '\''
        + '}';
  }
}
