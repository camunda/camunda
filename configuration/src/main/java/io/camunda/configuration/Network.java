/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.util.unit.DataSize;

/** Network configuration for cluster communication. */
public class Network {

  private static final String PREFIX = "camunda.cluster.network";

  private static final Map<String, String> LEGACY_GATEWAY_NETWORK_PROPERTIES =
      Map.of(
          "host", "zeebe.gateway.cluster.host",
          "advertisedHost", "zeebe.gateway.cluster.advertisedHost",
          "socketSendBuffer", "zeebe.gateway.cluster.socketSendBuffer",
          "socketReceiveBuffer", "zeebe.gateway.cluster.socketReceiveBuffer",
          "maxMessageSize", "zeebe.gateway.network.maxMessageSize");

  private static final Map<String, String> LEGACY_BROKER_NETWORK_PROPERTIES =
      Map.of(
          "host", "zeebe.broker.network.host",
          "advertisedHost", "zeebe.broker.network.advertisedHost",
          "portOffset", "zeebe.broker.network.portOffset",
          "maxMessageSize", "zeebe.broker.network.maxMessageSize",
          "socketSendBuffer", "zeebe.broker.network.socketSendBuffer",
          "socketReceiveBuffer", "zeebe.broker.network.socketReceiveBuffer",
          "heartbeatTimeout", "zeebe.broker.network.heartbeatTimeout",
          "heartbeatInterval", "zeebe.broker.network.heartbeatInterval");

  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_NETWORK_PROPERTIES;

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

  public String getHost() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".host",
        host,
        String.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("host")));
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public String getAdvertisedHost() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".advertised-host",
        advertisedHost,
        String.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("advertisedHost")));
  }

  public void setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
  }

  public int getPortOffset() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".port-offset",
        portOffset,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("portOffset")));
  }

  public void setPortOffset(final int portOffset) {
    this.portOffset = portOffset;
  }

  public DataSize getMaxMessageSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-message-size",
        maxMessageSize,
        DataSize.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("maxMessageSize")));
  }

  public void setMaxMessageSize(final DataSize maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  public DataSize getSocketSendBuffer() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".socket-send-buffer",
        socketSendBuffer,
        DataSize.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("socketSendBuffer")));
  }

  public void setSocketSendBuffer(final DataSize socketSendBuffer) {
    this.socketSendBuffer = socketSendBuffer;
  }

  public DataSize getSocketReceiveBuffer() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".socket-receive-buffer",
        socketReceiveBuffer,
        DataSize.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("socketReceiveBuffer")));
  }

  public void setSocketReceiveBuffer(final DataSize socketReceiveBuffer) {
    this.socketReceiveBuffer = socketReceiveBuffer;
  }

  public Duration getHeartbeatTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".heartbeat-timeout",
        heartbeatTimeout,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("heartbeatTimeout")));
  }

  public void setHeartbeatTimeout(final Duration heartbeatTimeout) {
    this.heartbeatTimeout = heartbeatTimeout;
  }

  public Duration getHeartbeatInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".heartbeat-interval",
        heartbeatInterval,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("heartbeatInterval")));
  }

  public void setHeartbeatInterval(final Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
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

    return copy;
  }

  public Network withBrokerNetworkProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_BROKER_NETWORK_PROPERTIES;
    return copy;
  }

  public Network withGatewayNetworkProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_GATEWAY_NETWORK_PROPERTIES;
    return copy;
  }
}
