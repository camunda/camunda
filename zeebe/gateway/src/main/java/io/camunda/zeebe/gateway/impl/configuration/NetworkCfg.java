/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_PORT;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.util.unit.DataSize;

public final class NetworkCfg {

  private String host;
  private int port = DEFAULT_PORT;
  private Duration minKeepAliveInterval = Duration.ofSeconds(30);
  private DataSize maxMessageSize = DataSize.ofMegabytes(4);

  /**
   * Maximum age of a gRPC connection before the server proactively closes it (via GOAWAY), forcing
   * the client to reconnect. Defaults to 5 minutes. Set to {@code null} to disable, i.e. keep
   * grpc-java's own default of unbounded connection age.
   */
  private @Nullable Duration maxConnectionAge = Duration.ofMinutes(5);

  /**
   * Grace period, after {@link #maxConnectionAge} elapses, during which existing calls on the
   * connection may finish before it is forcibly terminated. Only takes effect when {@link
   * #maxConnectionAge} is set. Defaults to 1 minute. Set to {@code null} to disable, i.e. keep
   * grpc-java's own default of an infinite grace period.
   */
  private @Nullable Duration maxConnectionAgeGrace = Duration.ofMinutes(1);

  public void init(final String defaultHost) {
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
    minKeepAliveInterval = keepAlive;
    return this;
  }

  public DataSize getMaxMessageSize() {
    return maxMessageSize;
  }

  public NetworkCfg setMaxMessageSize(final DataSize maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
    return this;
  }

  public @Nullable Duration getMaxConnectionAge() {
    return maxConnectionAge;
  }

  public NetworkCfg setMaxConnectionAge(final @Nullable Duration maxConnectionAge) {
    this.maxConnectionAge = maxConnectionAge;
    return this;
  }

  public @Nullable Duration getMaxConnectionAgeGrace() {
    return maxConnectionAgeGrace;
  }

  public NetworkCfg setMaxConnectionAgeGrace(final @Nullable Duration maxConnectionAgeGrace) {
    this.maxConnectionAgeGrace = maxConnectionAgeGrace;
    return this;
  }

  public InetSocketAddress toSocketAddress() {
    return new InetSocketAddress(host, port);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        host, port, minKeepAliveInterval, maxMessageSize, maxConnectionAge, maxConnectionAgeGrace);
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
    return port == that.port
        && Objects.equals(host, that.host)
        && Objects.equals(minKeepAliveInterval, that.minKeepAliveInterval)
        && Objects.equals(maxMessageSize, that.maxMessageSize)
        && Objects.equals(maxConnectionAge, that.maxConnectionAge)
        && Objects.equals(maxConnectionAgeGrace, that.maxConnectionAgeGrace);
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
