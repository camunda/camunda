/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg.CommandApiCfg;
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg.InternalApiCfg;
import org.springframework.util.unit.DataSize;

public final class NetworkCfg implements ConfigurationEntry {

  public static final int DEFAULT_COMMAND_API_PORT = 26501;
  public static final int DEFAULT_INTERNAL_API_PORT = 26502;
  public static final DataSize DEFAULT_MAX_MESSAGE_SIZE = DataSize.ofMegabytes(4);
  private static final String DEFAULT_HOST = "0.0.0.0";
  private static final String DEFAULT_ADVERTISED_HOST =
      Address.defaultAdvertisedHost().getHostAddress();

  // leave host and advertised host to null, so we can distinguish if they are set explicitly or not
  private String host = null;
  private String advertisedHost = null;
  private int portOffset = 0;
  private DataSize maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;

  private final CommandApiCfg commandApi = new CommandApiCfg();
  private InternalApiCfg internalApi = new InternalApiCfg();
  private SecurityCfg security = new SecurityCfg();

  @Override
  public void init(final BrokerCfg brokerCfg, final String brokerBase) {
    applyDefaults();
    security.init(brokerCfg, brokerBase);
  }

  public void applyDefaults() {
    commandApi.applyDefaults(this);
    internalApi.applyDefaults(this);
  }

  public String getHost() {
    return host == null ? DEFAULT_HOST : host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public String getAdvertisedHost() {
    if (advertisedHost != null) {
      return advertisedHost;
    }

    if (host != null) {
      return host;
    }

    return DEFAULT_ADVERTISED_HOST;
  }

  public void setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
  }

  public int getPortOffset() {
    return portOffset;
  }

  public void setPortOffset(final int portOffset) {
    this.portOffset = portOffset;
  }

  public long getMaxMessageSizeInBytes() {
    return maxMessageSize.toBytes();
  }

  public DataSize getMaxMessageSize() {
    return maxMessageSize;
  }

  public void setMaxMessageSize(final DataSize maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  public CommandApiCfg getCommandApi() {
    return commandApi;
  }

  public SocketBindingCfg getInternalApi() {
    return internalApi;
  }

  public void setInternalApi(final InternalApiCfg internalApi) {
    this.internalApi = internalApi;
  }

  public SecurityCfg getSecurity() {
    return security;
  }

  public void setSecurity(final SecurityCfg security) {
    this.security = security;
  }

  @Override
  public String toString() {
    return "NetworkCfg{"
        + "host='"
        + host
        + '\''
        + ", portOffset="
        + portOffset
        + '\''
        + ", maxMessageSize="
        + maxMessageSize
        + ", commandApi="
        + commandApi
        + ", internalApi="
        + internalApi
        + ", security="
        + security
        + '}';
  }
}
