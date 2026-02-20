/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.Camunda;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@SuppressWarnings({"unused", "resource"})
public final class TestClusterBuilder {

  private static final String DEFAULT_CLUSTER_NAME = "zeebe-cluster";

  private String name = DEFAULT_CLUSTER_NAME;

  private int gatewaysCount = 0;

  private int brokersCount = 1;
  private int partitionsCount = 1;
  private int replicationFactor = 1;
  private boolean useEmbeddedGateway = true;
  private boolean useRecordingExporter = true;

  private Consumer<TestApplication<?>> nodeConfig = node -> {};
  private BiConsumer<MemberId, TestStandaloneBroker> brokerConfig =
      (id, broker) -> broker.withUnauthenticatedAccess();
  private BiConsumer<MemberId, TestStandaloneGateway> gatewayConfig =
      (id, gateway) -> gateway.withUnauthenticatedAccess();

  private final Map<MemberId, TestStandaloneGateway> gateways = new HashMap<>();
  private final Map<MemberId, TestStandaloneBroker> brokers = new HashMap<>();
  private boolean setNodeId = true;

  /**
   * If true, the brokers created by this cluster will use embedded gateways. By default this is
   * true.
   *
   * @param useEmbeddedGateway true or false to enable the embedded gateway on the brokers
   * @return this builder instance for chaining
   */
  public TestClusterBuilder withEmbeddedGateway(final boolean useEmbeddedGateway) {
    this.useEmbeddedGateway = useEmbeddedGateway;
    return this;
  }

  /**
   * The number of standalone gateway to create in this cluster. By default, this is 0, and the
   * brokers have embedded gateways.
   *
   * <p>Note that this parameter has no impact on the use of embedded gateways, and a cluster can
   * contain both standalone and embedded gateways.
   *
   * @param gatewaysCount the number of standalone gateways to create
   * @return this builder instance for chaining
   */
  public TestClusterBuilder withGatewaysCount(final int gatewaysCount) {
    this.gatewaysCount = gatewaysCount;
    return this;
  }

  /**
   * The number of brokers to create in this cluster. By default, this is 1.
   *
   * <p>Note that it's possible to create a cluster with no brokers, as this is could be a valid
   * setup for testing purposes. If that's the case, the gateways will not wait for the topology to
   * be complete (as they cannot know the topology), and will not be configured with a contact
   * point.
   *
   * <p>NOTE: setting this to 0 will also set the replication factor and partitions count to 0.
   *
   * @param brokersCount the number of brokers to create
   * @return this builder instance for chaining
   */
  public TestClusterBuilder withBrokersCount(final int brokersCount) {
    if (brokersCount < 0) {
      throw new IllegalArgumentException(
          "Expected brokersCount to be at least 0, but was " + brokersCount);
    }

    this.brokersCount = brokersCount;
    if (brokersCount > 0) {
      partitionsCount = Math.max(partitionsCount, 1);
      replicationFactor = Math.max(replicationFactor, 1);
    } else {
      partitionsCount = 0;
      replicationFactor = 0;
    }

    return this;
  }

  /**
   * Will set the number of partitions to distribute across the cluster. For example, if there are 3
   * brokers, 3 partitions, but replication factor of 1, then each broker will get exactly one
   * partition.
   *
   * <p>Note that the number of partitions must be greater than or equal to 1! If you do not want to
   * have any brokers, then set {@link #withBrokersCount(int)} to 0 instead.
   *
   * @param partitionsCount the number of partitions to distribute across the cluster
   * @return this builder instance for chaining
   */
  public TestClusterBuilder withPartitionsCount(final int partitionsCount) {
    if (partitionsCount <= 0) {
      throw new IllegalArgumentException(
          "Expected partitionsCount to be at least 1, but was " + partitionsCount);
    }

    this.partitionsCount = partitionsCount;
    return this;
  }

  /**
   * Sets the replication factor for each partition in the cluster. Note that this cannot be less
   * than 1, or greater than the broker count (see {@link #withBrokersCount(int)}).
   *
   * @param replicationFactor the replication factor for each partition
   * @return this builder instance for chaining
   */
  public TestClusterBuilder withReplicationFactor(final int replicationFactor) {
    if (replicationFactor <= 0) {
      throw new IllegalArgumentException(
          "Expected replicationFactor to be at least 1, but was " + replicationFactor);
    }

    this.replicationFactor = replicationFactor;
    return this;
  }

  /**
   * Sets the name of the cluster. This can be used to prevent nodes in one cluster from
   * inadvertently communicating with nodes in another cluster.
   *
   * <p>Unless you're deploying multiple clusters in the same network, leave as is.
   *
   * @param name the cluster name
   * @return this builder instance for chaining
   */
  public TestClusterBuilder withName(final String name) {
    if (name == null || name.trim().length() < 3) {
      throw new IllegalArgumentException(
          "Expected cluster name to be at least 3 characters, but was " + name);
    }

    this.name = name;
    return this;
  }

  /**
   * Sets the configuration function that will be executed in the {@link #build()} method on both
   * brokers and gateways (embedded gateways included). NOTE: this configuration has the lowest
   * priority, e.g. other configurations ({@link #gatewayConfig} or {@link #brokerConfig}) will
   * override this configuration in case of conflicts.
   *
   * @param modifier the function that will be applied on all cluster nodes
   * @return this builder instance for chaining
   */
  public TestClusterBuilder withNodeConfig(final Consumer<TestApplication<?>> modifier) {
    nodeConfig = modifier;
    return this;
  }

  /**
   * Sets the configuration function that will be executed in the {@link #build()} method on each
   * gateway (including embedded gateways). The first argument of is the member ID of the gateway,
   * and the second argument is the gateway itself.
   *
   * <p>NOTE: in case of conflicts with {@link #nodeConfig} this configuration will override {@link
   * #nodeConfig}.
   *
   * <p>NOTE: in case of conflicts with this configuration is an embedded gateway configuration and
   * a broker configuration, broker configuration will override this configuration.
   *
   * @param modifier the function that will be applied on all cluster gateways (embedded ones
   *     included)
   * @return this builder instance for chaining
   */
  public TestClusterBuilder withGatewayConfig(
      final BiConsumer<MemberId, TestStandaloneGateway> modifier) {
    gatewayConfig = modifier;
    return this;
  }

  /**
   * Sets the configuration function that will be executed in the {@link #build()} method on each
   * gateway (including embedded gateways).
   *
   * <p>NOTE: in case of conflicts with {@link #nodeConfig} this configuration will override {@link
   * #nodeConfig}.
   *
   * <p>NOTE: in case of conflicts with this configuration is an embedded gateway configuration and
   * a broker configuration, broker configuration will override this configuration.
   *
   * @param modifier the function that will be applied on all cluster gateways (embedded ones
   *     included)
   * @return this builder instance for chaining
   */
  public TestClusterBuilder withGatewayConfig(final Consumer<TestStandaloneGateway> modifier) {
    gatewayConfig = (memberId, gateway) -> modifier.accept(gateway);
    return this;
  }

  /**
   * Sets the configuration function that will be executed in the {@link #build()} method on each
   * broker. The first argument is the broker ID, and the second argument is the broker itself.
   *
   * <p>NOTE: in case of conflicts with {@link #nodeConfig} or {@link #gatewayConfig} this
   * configuration will override them.
   *
   * @param modifier the function that will be applied on all cluster brokers
   * @return this builder instance for chaining
   */
  public TestClusterBuilder withBrokerConfig(
      final BiConsumer<MemberId, TestStandaloneBroker> modifier) {
    brokerConfig = modifier;
    return this;
  }

  /**
   * Sets the configuration function that will be executed in the {@link #build()} method on each
   * broker.
   *
   * <p>NOTE: in case of conflicts with {@link #nodeConfig} or {@link #gatewayConfig} this
   * configuration will override them.
   *
   * @return this builder instance for chaining
   */
  public TestClusterBuilder withBrokerConfig(final Consumer<TestStandaloneBroker> modifier) {
    brokerConfig = (id, broker) -> modifier.accept(broker);
    return this;
  }

  /**
   * If true, registers the {@link RecordingExporter} for each broker. Defaults to true.
   *
   * @param useRecordingExporter whether to enable the recording exporter
   * @return this builder instance for chaining
   */
  public TestClusterBuilder useRecordingExporter(final boolean useRecordingExporter) {
    this.useRecordingExporter = useRecordingExporter;
    return this;
  }

  public TestClusterBuilder withoutNodeId() {
    setNodeId = false;
    return this;
  }

  /**
   * Builds a new Zeebe cluster. Will create {@link #brokersCount} brokers (accessible later via
   * {@link TestCluster#brokers()}) and {@link #gatewaysCount} standalone gateways (accessible later
   * via {@link TestCluster#gateways()}).
   *
   * <p>If {@link #useEmbeddedGateway} is true, then all brokers will have the embedded gateway
   * enabled and the right topology check configured. Additionally, {@link TestCluster#gateways()}
   * will also include them, along with any other additional standalone gateway.
   *
   * <p>For standalone gateways, if {@link #brokersCount} is at least one, then a random broker is
   * picked as the contact point for all gateways.
   *
   * @return a new Zeebe cluster
   */
  public TestCluster build() {
    gateways.clear();
    brokers.clear();

    validate();
    createBrokers();

    // gateways are configured after brokers such that we can set the right contact point if there
    // is one
    createGateways();

    return new TestCluster(
        name, replicationFactor, partitionsCount, new HashMap<>(brokers), new HashMap<>(gateways));
  }

  private void applyConfigFunctions(final MemberId id, final TestApplication<?> zeebe) {
    nodeConfig.accept(zeebe);

    if (zeebe instanceof final TestStandaloneGateway gateway) {
      gatewayConfig.accept(id, gateway);
    }

    if (zeebe instanceof final TestStandaloneBroker broker) {
      brokerConfig.accept(id, broker);
    }
  }

  private void validate() {
    if (replicationFactor > brokersCount) {
      throw new IllegalStateException(
          "Expected replicationFactor to be less than or equal to brokersCount, but was "
              + replicationFactor
              + " > "
              + brokersCount);
    }

    if (brokersCount > 0) {
      if (partitionsCount < 1) {
        throw new IllegalStateException(
            "Expected to have at least one partition if there are any brokers, but partitionsCount"
                + " was "
                + partitionsCount);
      }

      if (replicationFactor < 1) {
        throw new IllegalStateException(
            "Expected to have replication factor at least 1 if there are any brokers, but"
                + " replicationFactor was "
                + replicationFactor);
      }
    }
  }

  private void createBrokers() {
    for (int i = 0; i < brokersCount; i++) {
      final var memberId = MemberId.from(String.valueOf(i));
      final var broker = createBroker(i);

      applyConfigFunctions(memberId, broker);
      brokers.put(memberId, broker);
    }

    // since initial contact points has to contain all known brokers, we can only configure it
    // AFTER the base broker configuration
    final var contactPoints = getInitialContactPoints();
    brokers.values().stream()
        .map(TestStandaloneBroker::unifiedConfig)
        .map(Camunda::getCluster)
        .forEach(cfg -> cfg.setInitialContactPoints(contactPoints));
  }

  private TestStandaloneBroker createBroker(final int index) {
    final TestStandaloneBroker broker =
        new TestStandaloneBroker()
            .withUnifiedConfig(
                uc -> {
                  final var cluster = uc.getCluster();
                  if (setNodeId) {
                    cluster.setNodeId(index);
                  }
                  cluster.setPartitionCount(partitionsCount);
                  cluster.setReplicationFactor(replicationFactor);
                  cluster.setSize(brokersCount);
                  cluster.setName(name);
                })
            .withUnifiedConfig(
                uc -> {
                  final var replicas = (partitionsCount * replicationFactor) / brokersCount;
                  uc.getSystem().setIoThreadCount(replicas);
                  uc.getSystem().setCpuThreadCount(replicas);
                })
            .withUnifiedConfig(
                uc -> uc.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false))
            .withGatewayEnabled(useEmbeddedGateway)
            .withRecordingExporter(useRecordingExporter);
    return broker;
  }

  private void createGateways() {
    for (int i = 0; i < gatewaysCount; i++) {
      final var id = "gateway-" + i;
      final var memberId = MemberId.from(id);
      final var gateway = createGateway(id, i);

      applyConfigFunctions(memberId, gateway);
      gateways.put(memberId, gateway);
    }
  }

  private TestStandaloneGateway createGateway(final String id, final int index) {
    return new TestStandaloneGateway()
        .withUnifiedConfig(
            uc -> {
              final var cluster = uc.getCluster();
              cluster.setNodeId(index);
              cluster.setGatewayId(id);
              cluster.setName(name);
              cluster.setInitialContactPoints(getInitialContactPoints());
            });
  }

  private List<String> getInitialContactPoints() {
    return brokers.values().stream()
        .map(builder -> builder.address(TestZeebePort.CLUSTER))
        .toList();
  }
}
