/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration;

import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_PORT;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_HOST;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_KEEP_ALIVE_INTERVAL;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_PORT;

import io.zeebe.transport.impl.SocketAddress;
import io.zeebe.util.Environment;
import java.time.Duration;
import java.util.Objects;

public final class NetworkCfg {

  private String host;
  private int port = DEFAULT_PORT;
  private Duration minKeepAliveInterval = Duration.ofSeconds(30);

  public void init(final Environment environment, final String defaultHost) {
    environment.get(ENV_GATEWAY_HOST).ifPresent(this::setHost);
    environment.getInt(ENV_GATEWAY_PORT).ifPresent(this::setPort);
    environment
        .get(ENV_GATEWAY_KEEP_ALIVE_INTERVAL)
        .ifPresent(value -> setMinKeepAliveInterval(Duration.parse(value)));

    if (host == null) {
      host = defaultHost;
    }
  }

  public String getHost() {
    return host;
  }

  public NetworkCfg setHost(final String host) {
    this.host = host;
    return this;
  }

  public int getPort() {
    return port;
  }

  public NetworkCfg setPort(final int port) {
    this.port = port;
    return this;
  }

  public Duration getMinKeepAliveInterval() {
    return minKeepAliveInterval;
  }

  public NetworkCfg setMinKeepAliveInterval(final Duration keepAlive) {
    this.minKeepAliveInterval = keepAlive;
    return this;
  }

  public SocketAddress toSocketAddress() {
    return new SocketAddress(host, port);
  }

  @Override
  public int hashCode() {
    return Objects.hash(host, port);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final NetworkCfg that = (NetworkCfg) o;
    return port == that.port && Objects.equals(host, that.host);
  }

  @Override
  public String toString() {
    return "NetworkCfg{"
        + "host='"
        + host
        + '\''
        + ", port="
        + port
        + ", minKeepAliveInterval="
        + minKeepAliveInterval
        + '}';
  }
}
