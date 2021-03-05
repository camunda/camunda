/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.configuration;

import java.net.InetSocketAddress;
import java.util.Optional;

public class SocketBindingCfg {

  private final int defaultPort;
  private String host;
  private Integer port;
  private String advertisedHost;
  private Integer advertisedPort;

  private SocketBindingCfg(final int defaultPort) {
    this.defaultPort = defaultPort;
  }

  public InetSocketAddress getAddress() {
    return new InetSocketAddress(host, port);
  }

  public InetSocketAddress getAdvertisedAddress() {
    return new InetSocketAddress(advertisedHost, advertisedPort);
  }

  public void applyDefaults(final NetworkCfg networkCfg) {
    advertisedHost =
        Optional.ofNullable(advertisedHost)
            .orElseGet(() -> Optional.ofNullable(host).orElseGet(networkCfg::getAdvertisedHost));
    host = Optional.ofNullable(host).orElse(networkCfg.getHost());
    port = Optional.ofNullable(port).orElse(defaultPort) + networkCfg.getPortOffset() * 10;
    advertisedPort =
        Optional.ofNullable(advertisedPort)
            .map(p -> p + networkCfg.getPortOffset() * 10)
            .orElse(port);
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public String getAdvertisedHost() {
    return advertisedHost;
  }

  public void setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
  }

  public int getPort() {
    return port;
  }

  public void setPort(final int port) {
    this.port = port;
  }

  public int getAdvertisedPort() {
    return advertisedPort;
  }

  public void setAdvertisedPort(final int advertisedPort) {
    this.advertisedPort = advertisedPort;
  }

  @Override
  public String toString() {
    return "SocketBindingCfg{"
        + "host='"
        + host
        + '\''
        + ", port="
        + port
        + ", advertisedHost="
        + advertisedHost
        + ", advertisedPort="
        + advertisedPort
        + "}";
  }

  public static class CommandApiCfg extends SocketBindingCfg {
    public CommandApiCfg() {
      super(NetworkCfg.DEFAULT_COMMAND_API_PORT);
    }
  }

  public static class InternalApiCfg extends SocketBindingCfg {
    public InternalApiCfg() {
      super(NetworkCfg.DEFAULT_INTERNAL_API_PORT);
    }
  }

  public static class MonitoringApiCfg extends SocketBindingCfg {
    public MonitoringApiCfg() {
      super(NetworkCfg.DEFAULT_MONITORING_API_PORT);
    }
  }
}
