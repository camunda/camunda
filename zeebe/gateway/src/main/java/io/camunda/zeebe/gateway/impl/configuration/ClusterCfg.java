/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import static io.camunda.zeebe.gateway.Loggers.GATEWAY_CFG_LOGGER;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_HOST;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_MEMBER_ID;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_NAME;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_PORT;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CONTACT_POINT_HOST;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CONTACT_POINT_PORT;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_REQUEST_TIMEOUT;
import static io.camunda.zeebe.util.StringUtil.LIST_SANITIZER;

import io.atomix.cluster.messaging.MessagingConfig.CompressionAlgorithm;
import io.atomix.utils.net.Address;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.util.unit.DataSize;

public final class ClusterCfg {

  private static final String DEFAULT_ADVERTISED_HOST =
      Address.defaultAdvertisedHost().getHostAddress();
  private List<String> initialContactPoints =
      Collections.singletonList(DEFAULT_CONTACT_POINT_HOST + ":" + DEFAULT_CONTACT_POINT_PORT);
  private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
  private String clusterName = DEFAULT_CLUSTER_NAME;
  private String memberId = DEFAULT_CLUSTER_MEMBER_ID;
  // leave host and advertised host to null, so we can distinguish if they are set explicitly or not
  private String host = null;
  private String advertisedHost = null;
  private int port = DEFAULT_CLUSTER_PORT;
  private Integer advertisedPort = null;
  private MembershipCfg membership = new MembershipCfg();
  private SecurityCfg security = new SecurityCfg();
  private CompressionAlgorithm messageCompression = CompressionAlgorithm.NONE;
  private ConfigManagerCfg configManager = ConfigManagerCfg.defaultConfig();
  private DataSize socketSendBuffer = null;
  private DataSize socketReceiveBuffer = null;

  public String getMemberId() {
    return memberId;
  }

  public ClusterCfg setMemberId(final String memberId) {
    this.memberId = memberId;
    return this;
  }

  public String getHost() {
    return host != null ? host : DEFAULT_CLUSTER_HOST;
  }

  public ClusterCfg setHost(final String host) {
    this.host = host;
    return this;
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

  public ClusterCfg setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
    return this;
  }

  public int getPort() {
    return port;
  }

  public ClusterCfg setPort(final int port) {
    this.port = port;
    return this;
  }

  public int getAdvertisedPort() {
    return Optional.ofNullable(advertisedPort).orElseGet(this::getPort);
  }

  public ClusterCfg setAdvertisedPort(final int advertisedPort) {
    this.advertisedPort = advertisedPort;
    return this;
  }

  @Deprecated(since = "8.1.0", forRemoval = true)
  public ClusterCfg setContactPoint(final String contactPoint) {
    GATEWAY_CFG_LOGGER.warn(
        "Configuring deprecated property 'contactPoint', will use 'initialContactPoints'. Please consider to migrate to 'initialContactPoints' property, which allows to set a list of contact points.");
    setInitialContactPoints(Collections.singletonList(contactPoint));
    return this;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public ClusterCfg setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  public String getClusterName() {
    return clusterName;
  }

  public ClusterCfg setClusterName(final String name) {
    clusterName = name;
    return this;
  }

  public MembershipCfg getMembership() {
    return membership;
  }

  public void setMembership(final MembershipCfg membership) {
    this.membership = membership;
  }

  public SecurityCfg getSecurity() {
    return security;
  }

  public ClusterCfg setSecurity(final SecurityCfg security) {
    this.security = security;
    return this;
  }

  public CompressionAlgorithm getMessageCompression() {
    return messageCompression;
  }

  public void setMessageCompression(final CompressionAlgorithm compressionAlgorithm) {
    messageCompression = compressionAlgorithm;
  }

  public List<String> getInitialContactPoints() {
    return initialContactPoints;
  }

  public ClusterCfg setInitialContactPoints(final List<String> initialContactPoints) {
    this.initialContactPoints = LIST_SANITIZER.apply(initialContactPoints);
    return this;
  }

  public ConfigManagerCfg getConfigManager() {
    return configManager;
  }

  public ClusterCfg setConfigManager(final ConfigManagerCfg configManagerCfg) {
    configManager = configManagerCfg;
    return this;
  }

  public DataSize getSocketSendBuffer() {
    return socketSendBuffer;
  }

  public ClusterCfg setSocketSendBuffer(final DataSize socketSendBuffer) {
    this.socketSendBuffer = socketSendBuffer;
    return this;
  }

  public DataSize getSocketReceiveBuffer() {
    return socketReceiveBuffer;
  }

  public ClusterCfg setSocketReceiveBuffer(final DataSize socketReceiveBuffer) {
    this.socketReceiveBuffer = socketReceiveBuffer;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        initialContactPoints,
        requestTimeout,
        clusterName,
        memberId,
        host,
        port,
        membership,
        security,
        messageCompression,
        configManager,
        socketSendBuffer,
        socketReceiveBuffer);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClusterCfg that = (ClusterCfg) o;
    return port == that.port
        && Objects.equals(initialContactPoints, that.initialContactPoints)
        && Objects.equals(requestTimeout, that.requestTimeout)
        && Objects.equals(clusterName, that.clusterName)
        && Objects.equals(memberId, that.memberId)
        && Objects.equals(host, that.host)
        && Objects.equals(membership, that.membership)
        && Objects.equals(security, that.security)
        && Objects.equals(messageCompression, that.messageCompression)
        && Objects.equals(configManager, that.configManager)
        && Objects.equals(socketSendBuffer, that.socketSendBuffer)
        && Objects.equals(socketReceiveBuffer, that.socketReceiveBuffer);
  }

  @Override
  public String toString() {
    return "ClusterCfg{"
        + "initialContactPoints="
        + initialContactPoints
        + ", requestTimeout="
        + requestTimeout
        + ", clusterName='"
        + clusterName
        + '\''
        + ", memberId='"
        + memberId
        + '\''
        + ", host='"
        + host
        + '\''
        + ", port="
        + port
        + ", membership="
        + membership
        + ", security="
        + security
        + ", messageCompression="
        + messageCompression
        + ", configManagerCfg="
        + configManager
        + ", socketSendBuffer="
        + socketSendBuffer
        + ", socketReceiveBuffer="
        + socketReceiveBuffer
        + '}';
  }
}
