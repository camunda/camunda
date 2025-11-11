/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CLUSTER_NAME;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CONTACT_POINT_HOST;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_CONTACT_POINT_PORT;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenersCfg;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.core.ResolvableType;

public class Cluster implements Cloneable {

  // Property names for initial contact points configuration
  public static final String LEGACY_INITIAL_CONTACT_POINTS_PROPERTY;
  private static final String PREFIX = "camunda.cluster";
  public static final String UNIFIED_INITIAL_CONTACT_POINTS_PROPERTY =
      PREFIX + ".initial-contact-points";

  private static final Map<String, String> LEGACY_GATEWAY_PROPERTIES =
      Map.of(
          "messageCompression",
          "zeebe.gateway.cluster.messageCompression",
          "clusterName",
          "zeebe.gateway.cluster.clusterName",
          "initialContactPoints",
          "zeebe.gateway.cluster.initialContactPoints");

  private static final Map<String, String> LEGACY_BROKER_PROPERTIES =
      Map.of(
          "nodeId", "zeebe.broker.cluster.nodeId",
          "partitionsCount", "zeebe.broker.cluster.partitionsCount",
          "replicationFactor", "zeebe.broker.cluster.replicationFactor",
          "clusterSize", "zeebe.broker.cluster.clusterSize",
          "messageCompression", "zeebe.broker.cluster.messageCompression",
          "clusterName", "zeebe.broker.cluster.clusterName",
          "initialContactPoints", "zeebe.broker.cluster.initialContactPoints");

  static {
    LEGACY_INITIAL_CONTACT_POINTS_PROPERTY =
        Objects.requireNonNull(LEGACY_BROKER_PROPERTIES.get("initialContactPoints"));
  }

  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_PROPERTIES;

  /** Configuration for the distributed metadata manager in the cluster. */
  @NestedConfigurationProperty private Metadata metadata = new Metadata();

  /** Network configuration for cluster communication. */
  @NestedConfigurationProperty private Network network = new Network();

  /**
   * Allows to specify a list of known other nodes to connect to on startup. The contact points of
   * the internal network configuration must be specified.
   *
   * <p>The format is [HOST:PORT]
   *
   * <p>Example: initialContactPoints : [ 192.168.1.22:26502, 192.168.1.32:26502 ]
   *
   * <p>To guarantee the cluster can survive network partitions, all nodes must be specified as
   * initial contact points.
   */
  private List<String> initialContactPoints = Collections.emptyList();

  /**
   * Specifies the unique id of this broker node in a cluster. The id should be between 0 and number
   * of nodes in the cluster (exclusive).
   */
  private int nodeId = 0;

  /** The number of partitions in the cluster. */
  private int partitionCount = 1;

  /**
   * The number of replicas for each partition in the cluster. The replication factor cannot be
   * greater than the number of nodes in the cluster.
   */
  private int replicationFactor = 1;

  /** The number of nodes in the cluster. */
  private int size = 1;

  /**
   * Configure parameters for SWIM protocol which is used to propagate cluster membership #
   * information among brokers and gateways
   */
  @NestedConfigurationProperty private Membership membership = new Membership();

  /** Set the name of the cluster */
  private String name = DEFAULT_CLUSTER_NAME;

  /** Configuration for the Raft protocol in the cluster. */
  @NestedConfigurationProperty private Raft raft = new Raft();

  /**
   * Configure compression algorithm for all message sent between the brokers and between the broker
   * and the gateway. Available options are NONE, GZIP and SNAPPY. This feature is useful when the
   * network latency between the brokers is very high (for example when the brokers are deployed in
   * different data centers). When latency is high, the network bandwidth is severely reduced. Hence
   * enabling compression helps to improve the throughput.
   *
   * <p>Note: When there is no latency enabling this may have a performance impact.
   */
  private CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.NONE;

  /**
   * Configuration for global listeners defined at cluster-level instead of directly in the BPMN
   * model.
   */
  @NestedConfigurationProperty
  private GlobalListenersCfg globalListeners = new GlobalListenersCfg();

  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(final Metadata metadata) {
    this.metadata = metadata;
  }

  public Network getNetwork() {
    return network;
  }

  public void setNetwork(final Network network) {
    this.network = network;
  }

  public List<String> getInitialContactPoints() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        UNIFIED_INITIAL_CONTACT_POINTS_PROPERTY,
        initialContactPoints,
        ResolvableType.forClassWithGenerics(List.class, String.class),
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("initialContactPoints")));
  }

  public void setInitialContactPoints(final List<String> initialContactPoints) {
    this.initialContactPoints = initialContactPoints;
  }

  public int getNodeId() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".node-id",
        nodeId,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("nodeId")));
  }

  public void setNodeId(final int nodeId) {
    this.nodeId = nodeId;
  }

  public int getPartitionCount() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".partition-count",
        partitionCount,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("partitionsCount")));
  }

  public void setPartitionCount(final int partitionCount) {
    this.partitionCount = partitionCount;
  }

  public int getReplicationFactor() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".replication-factor",
        replicationFactor,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("replicationFactor")));
  }

  public void setReplicationFactor(final int replicationFactor) {
    this.replicationFactor = replicationFactor;
  }

  public int getSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".size",
        size,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("clusterSize")));
  }

  public void setSize(final int size) {
    this.size = size;
  }

  public Membership getMembership() {
    return membership;
  }

  public void setMembership(final Membership membership) {
    this.membership = membership;
  }

  public String getName() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".name",
        name,
        String.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("clusterName")));
  }

  public void setName(final String name) {
    this.name = name;
  }

  public Raft getRaft() {
    return raft;
  }

  public void setRaft(final Raft raft) {
    this.raft = raft;
  }

  public CompressionAlgorithm getCompressionAlgorithm() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".compression-algorithm",
        compressionAlgorithm,
        CompressionAlgorithm.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("messageCompression")));
  }

  public void setCompressionAlgorithm(final CompressionAlgorithm compressionAlgorithm) {
    this.compressionAlgorithm = compressionAlgorithm;
  }

  public GlobalListenersCfg getGlobalListeners() {
    return globalListeners;
  }

  public void setGlobalListeners(final GlobalListenersCfg globalListeners) {
    this.globalListeners = globalListeners;
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (final CloneNotSupportedException e) {
      throw new AssertionError("Unexpected: Class must implement Cloneable", e);
    }
  }

  public Cluster withBrokerProperties() {
    final var copy = (Cluster) clone();
    copy.legacyPropertiesMap = LEGACY_BROKER_PROPERTIES;
    return copy;
  }

  public Cluster withGatewayProperties() {
    final var copy = (Cluster) clone();
    copy.legacyPropertiesMap = LEGACY_GATEWAY_PROPERTIES;

    if (copy.initialContactPoints.isEmpty()) {
      copy.initialContactPoints =
          List.of(DEFAULT_CONTACT_POINT_HOST + ":" + DEFAULT_CONTACT_POINT_PORT);
    }
    return copy;
  }

  public enum CompressionAlgorithm {
    GZIP,
    NONE,
    SNAPPY
  }
}
