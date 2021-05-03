/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.configuration;

import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MONITORING_ENABLED;
import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MONITORING_PORT;

import java.net.InetSocketAddress;
import java.util.Objects;

public final class MonitoringCfg {

  private boolean enabled = DEFAULT_MONITORING_ENABLED;

  private String host;
  private int port = DEFAULT_MONITORING_PORT;

  public void init(final String defaultHost) {
    if (host == null) {
      host = defaultHost;
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  public MonitoringCfg setEnabled(final boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public String getHost() {
    return host;
  }

  public MonitoringCfg setHost(final String host) {
    this.host = host;
    return this;
  }

  public int getPort() {
    return port;
  }

  public MonitoringCfg setPort(final int port) {
    this.port = port;
    return this;
  }

  public InetSocketAddress toSocketAddress() {
    return new InetSocketAddress(host, port);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, host, port);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MonitoringCfg that = (MonitoringCfg) o;
    return enabled == that.enabled && port == that.port && Objects.equals(host, that.host);
  }

  @Override
  public String toString() {
    return "MonitoringCfg{"
        + "enabled="
        + enabled
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + '}';
  }
}
