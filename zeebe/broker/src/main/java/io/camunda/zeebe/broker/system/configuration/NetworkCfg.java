/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg.CommandApiCfg;
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg.InternalApiCfg;
import java.time.Duration;
import java.util.Optional;
import org.springframework.util.unit.DataSize;

public final class NetworkCfg implements ConfigurationEntry {

  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final int DEFAULT_COMMAND_API_PORT = 26501;
  public static final int DEFAULT_INTERNAL_API_PORT = 26502;
  public static final DataSize DEFAULT_MAX_MESSAGE_SIZE = DataSize.ofMegabytes(4);
  private static final DataSize DEFAULT_BROKER_SOCKET_SEND_BUFFER = DataSize.ofMegabytes(1);
  private static final DataSize DEFAULT_BROKER_SOCKET_RECEIVE_BUFFER = DataSize.ofMegabytes(1);

  private String host = DEFAULT_HOST;
  private int portOffset = 0;
  private DataSize maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
  private String advertisedHost;
  private Duration heartbeatTimeout = Duration.ofSeconds(15);
  private Duration heartbeatInterval = Duration.ofSeconds(5);
  private DataSize socketSendBuffer = DEFAULT_BROKER_SOCKET_SEND_BUFFER;
  private DataSize socketReceiveBuffer = DEFAULT_BROKER_SOCKET_RECEIVE_BUFFER;

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
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public String getAdvertisedHost() {
    return Optional.ofNullable(advertisedHost).orElse(getHost());
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

  public Duration getHeartbeatTimeout() {
    return heartbeatTimeout;
  }

  public void setHeartbeatTimeout(final Duration heartbeatTimeout) {
    this.heartbeatTimeout = heartbeatTimeout;
  }

  public Duration getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public void setHeartbeatInterval(final Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  public DataSize getSocketSendBuffer() {
    return socketSendBuffer;
  }

  public void setSocketSendBuffer(final DataSize socketSendBuffer) {
    this.socketSendBuffer = socketSendBuffer;
  }

  public DataSize getSocketReceiveBuffer() {
    return socketReceiveBuffer;
  }

  public void setSocketReceiveBuffer(final DataSize socketReceiveBuffer) {
    this.socketReceiveBuffer = socketReceiveBuffer;
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
        + ", heartbeatTimeout="
        + heartbeatTimeout
        + ", heartbeatInterval="
        + heartbeatInterval
        + ", socketReceiveBuffer="
        + socketReceiveBuffer
        + ", socketSendBuffer="
        + socketSendBuffer
        + ", commandApi="
        + commandApi
        + ", internalApi="
        + internalApi
        + ", security="
        + security
        + '}';
  }
}
