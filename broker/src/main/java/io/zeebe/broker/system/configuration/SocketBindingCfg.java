/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.transport.SocketAddress;
import java.util.Optional;

public class SocketBindingCfg {

  protected String host;
  protected int port;

  private SocketBindingCfg(final int defaultPort) {
    port = defaultPort;
  }

  public SocketAddress toSocketAddress() {
    return new SocketAddress(host, port);
  }

  public void applyDefaults(final NetworkCfg networkCfg) {

    host = Optional.ofNullable(host).orElse(networkCfg.getHost());

    port += networkCfg.getPortOffset() * 10;
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(final int port) {
    this.port = port;
  }

  @Override
  public String toString() {
    return "SocketBindingCfg{" + "host='" + host + '\'' + ", port=" + port + '}';
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
