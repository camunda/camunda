/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.cluster;

import static io.camunda.zeebe.util.StringUtil.LIST_SANITIZER;

import io.atomix.cluster.messaging.MessagingConfig.CompressionAlgorithm;
import io.atomix.utils.net.Address;
import io.camunda.migration.identity.config.ConfigManagerConfig;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ClusterProperties {

  public static final String DEFAULT_CONTACT_POINT_HOST = "127.0.0.1";
  public static final int DEFAULT_CONTACT_POINT_PORT = 26502;
  public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(15);

  public static final String DEFAULT_CLUSTER_NAME = "zeebe-cluster";
  public static final String DEFAULT_CLUSTER_MEMBER_ID = "migration";
  public static final String DEFAULT_CLUSTER_HOST = "0.0.0.0";
  public static final int DEFAULT_CLUSTER_PORT = 26502;

  public static final String DEFAULT_ADVERTISED_HOST =
      Address.defaultAdvertisedHost().getHostAddress();
  public static final Duration DEFAULT_AWAIT_CLUSTER_JOIN_RETRY_INTERVAL = Duration.ofSeconds(1);
  public static final int DEFAULT_AWAIT_CLUSTER_JOIN_MAX_ATTEMPTS = 30;
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
  private MembershipConfig membership = new MembershipConfig();
  private SecurityConfig security = new SecurityConfig();
  private CompressionAlgorithm messageCompression = CompressionAlgorithm.NONE;
  private ConfigManagerConfig configManager = ConfigManagerConfig.defaultConfig();
  private Duration awaitClusterJoinRetryInterval = DEFAULT_AWAIT_CLUSTER_JOIN_RETRY_INTERVAL;
  private int awaitClusterJoinMaxAttempts = DEFAULT_AWAIT_CLUSTER_JOIN_MAX_ATTEMPTS;

  public String getMemberId() {
    return memberId;
  }

  public ClusterProperties setMemberId(final String memberId) {
    this.memberId = memberId;
    return this;
  }

  public String getHost() {
    return host != null ? host : DEFAULT_CLUSTER_HOST;
  }

  public ClusterProperties setHost(final String host) {
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

  public ClusterProperties setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
    return this;
  }

  public int getPort() {
    return port;
  }

  public ClusterProperties setPort(final int port) {
    this.port = port;
    return this;
  }

  public int getAdvertisedPort() {
    return Optional.ofNullable(advertisedPort).orElseGet(this::getPort);
  }

  public ClusterProperties setAdvertisedPort(final int advertisedPort) {
    this.advertisedPort = advertisedPort;
    return this;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public ClusterProperties setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  public String getClusterName() {
    return clusterName;
  }

  public ClusterProperties setClusterName(final String name) {
    clusterName = name;
    return this;
  }

  public MembershipConfig getMembership() {
    return membership;
  }

  public void setMembership(final MembershipConfig membership) {
    this.membership = membership;
  }

  public SecurityConfig getSecurity() {
    return security;
  }

  public ClusterProperties setSecurity(final SecurityConfig security) {
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

  public ClusterProperties setInitialContactPoints(final List<String> initialContactPoints) {
    this.initialContactPoints = LIST_SANITIZER.apply(initialContactPoints);
    return this;
  }

  public ConfigManagerConfig getConfigManager() {
    return configManager;
  }

  public ClusterProperties setConfigManager(final ConfigManagerConfig configManagerCfg) {
    configManager = configManagerCfg;
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
        configManager);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClusterProperties that = (ClusterProperties) o;
    return port == that.port
        && Objects.equals(initialContactPoints, that.initialContactPoints)
        && Objects.equals(requestTimeout, that.requestTimeout)
        && Objects.equals(clusterName, that.clusterName)
        && Objects.equals(memberId, that.memberId)
        && Objects.equals(host, that.host)
        && Objects.equals(membership, that.membership)
        && Objects.equals(security, that.security)
        && Objects.equals(messageCompression, that.messageCompression)
        && Objects.equals(configManager, that.configManager);
  }

  @Override
  public String toString() {
    return "ClusterProperties{"
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
        + '}';
  }

  public Duration getAwaitClusterJoinRetryInterval() {
    return awaitClusterJoinRetryInterval;
  }

  public void setAwaitClusterJoinRetryInterval(final Duration awaitClusterJoinRetryInterval) {
    this.awaitClusterJoinRetryInterval = awaitClusterJoinRetryInterval;
  }

  public int getAwaitClusterJoinMaxAttempts() {
    return awaitClusterJoinMaxAttempts;
  }

  public void setAwaitClusterJoinMaxAttempts(final int awaitClusterJoinMaxAttempts) {
    this.awaitClusterJoinMaxAttempts = awaitClusterJoinMaxAttempts;
  }
}
