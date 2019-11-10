/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration;

import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MONITORING_ENABLED;
import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MONITORING_PORT;
import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MONITORING_TRACING_ENABLED;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_MONITORING_ENABLED;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_MONITORING_HOST;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_MONITORING_PORT;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_MONITORING_TRACING;

import io.zeebe.transport.impl.SocketAddress;
import io.zeebe.util.Environment;
import java.util.Objects;

public final class MonitoringCfg {

  private boolean enabled = DEFAULT_MONITORING_ENABLED;
  private boolean tracing = DEFAULT_MONITORING_TRACING_ENABLED;

  private String host;
  private int port = DEFAULT_MONITORING_PORT;

  public void init(final Environment environment, final String defaultHost) {
    environment.getBool(ENV_GATEWAY_MONITORING_ENABLED).ifPresent(this::setEnabled);
    environment.get(ENV_GATEWAY_MONITORING_HOST).ifPresent(this::setHost);
    environment.getInt(ENV_GATEWAY_MONITORING_PORT).ifPresent(this::setPort);
    environment.getBool(ENV_GATEWAY_MONITORING_TRACING).ifPresent(this::setTracing);

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

  public SocketAddress toSocketAddress() {
    return new SocketAddress(host, port);
  }

  public boolean isTracing() {
    return tracing;
  }

  public void setTracing(final boolean tracing) {
    this.tracing = tracing;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, tracing, host, port);
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
    return enabled == that.enabled
        && tracing == that.tracing
        && port == that.port
        && Objects.equals(host, that.host);
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
        + ", tracing="
        + tracing
        + '}';
  }
}
