/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.unit.DataSize;

/** Network configuration for cluster communication. */
public class Network {

  /**
   * Controls the default host the broker should bind to. Can be overwritten on a per-binding basis
   * for client, management and replication
   */
  private String host = null; // Do not set a default value.

  /**
   * Controls the advertised host. This is particularly useful if your broker stands behind a proxy.
   * If not set, its default is computed as: - If camunda.cluster.network.host was explicitly, use
   * this. - If not, try to resolve the machine's hostname to an IP address and use that. - If the
   * hostname is not resolvable, use the first non-loopback IP address. - If there is none, use the
   * loopback address.
   */
  private String advertisedHost = null; // Do not set a default value.

  /**
   * If a port offset is set it will be added to all ports specified in the config or the default
   * values. This is a shortcut to not always specifying every port. The offset will be added to the
   * second last position of the port, as Zeebe requires multiple ports. As example a portOffset of
   * 5 will increment all ports by 50, i.e. 26500 will become 26550 and so on.
   */
  private int portOffset = 0;

  /** Sets the maximum size of the incoming and outgoing messages (i.e. commands and events). */
  private DataSize maxMessageSize = DataSize.ofMegabytes(4);

  /**
   * Sets the size of the socket send buffer (SO_SNDBUF), for example 1MB. When not set (the
   * default), the operating system can determine the optimal size automatically.
   */
  private DataSize socketSendBuffer = null;

  /**
   * Sets the size of the socket receive buffer (SO_RCVBUF), for example 1MB. When not set (the
   * default), the operating system can determine the optimal size automatically.
   */
  private DataSize socketReceiveBuffer = null;

  /** Connections that did not receive any message within the specified timeout will be closed. */
  private Duration heartbeatTimeout = Duration.ofSeconds(15);

  /**
   * Sends a heartbeat when no other data is sent over an open connection within the specified
   * timeout. This is to ensure that the connection is kept open.
   */
  private Duration heartbeatInterval = Duration.ofSeconds(5);

  /** Sets the internal api configuration */
  @NestedConfigurationProperty private InternalApi internalApi = new InternalApi();

  @NestedConfigurationProperty private CommandApi commandApi = new CommandApi();

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

  public int getPortOffset() {
    return portOffset;
  }

  public void setPortOffset(final int portOffset) {
    this.portOffset = portOffset;
  }

  public DataSize getMaxMessageSize() {
    return maxMessageSize;
  }

  public void setMaxMessageSize(final DataSize maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
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

  public InternalApi getInternalApi() {
    return internalApi;
  }

  public void setInternalApi(final InternalApi internalApi) {
    this.internalApi = internalApi;
  }

  public CommandApi getCommandApi() {
    return commandApi;
  }

  public void setCommandApi(final CommandApi commandApi) {
    this.commandApi = commandApi;
  }

  @Override
  public Network clone() {
    final Network copy = new Network();
    copy.host = host;
    copy.advertisedHost = advertisedHost;
    copy.portOffset = portOffset;
    copy.maxMessageSize = maxMessageSize;
    copy.socketSendBuffer = socketSendBuffer;
    copy.socketReceiveBuffer = socketReceiveBuffer;
    copy.heartbeatTimeout = heartbeatTimeout;
    copy.heartbeatInterval = heartbeatInterval;
    copy.internalApi = internalApi;

    return copy;
  }
}
