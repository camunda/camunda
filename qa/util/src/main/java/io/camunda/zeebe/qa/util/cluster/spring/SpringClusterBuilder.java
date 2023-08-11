/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster.spring;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public final class SpringClusterBuilder {

  private static final String DEFAULT_CLUSTER_NAME = "zeebe-cluster";

  private String name = DEFAULT_CLUSTER_NAME;

  private int gatewaysCount = 0;

  private int brokersCount = 1;
  private int partitionsCount = 1;
  private int replicationFactor = 1;
  private boolean useEmbeddedGateway = true;
  private Path directory;
  private final boolean useRecordingExporter = true;

  private Consumer<AbstractSpringBuilder<?, ?, ?>> nodeConfig = builder -> {};
  private BiConsumer<MemberId, SpringBrokerNode.Builder> brokerConfig = (id, builder) -> {};
  private BiConsumer<MemberId, SpringGatewayNode.Builder> gatewayConfig = (memberId, builder) -> {};

  private final Map<MemberId, SpringGatewayNode.Builder> gateways = new HashMap<>();
  private final Map<MemberId, SpringBrokerNode.Builder> brokers = new HashMap<>();

  /**
   * If true, the brokers created by this cluster will use embedded gateways. By default this is
   * true.
   *
   * @param useEmbeddedGateway true or false to enable the embedded gateway on the brokers
   * @return this builder instance for chaining
   */
  public SpringClusterBuilder withEmbeddedGateway(final boolean useEmbeddedGateway) {
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
  public SpringClusterBuilder withGatewaysCount(final int gatewaysCount) {
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
  public SpringClusterBuilder withBrokersCount(final int brokersCount) {
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
  public SpringClusterBuilder withPartitionsCount(final int partitionsCount) {
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
  public SpringClusterBuilder withReplicationFactor(final int replicationFactor) {
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
  public SpringClusterBuilder withName(final String name) {
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
  public SpringClusterBuilder withNodeConfig(
      final Consumer<AbstractSpringBuilder<?, ?, ?>> modifier) {
    nodeConfig = modifier;
    return this;
  }

  /**
   * Sets the configuration function that will be executed in the {@link #build()} method on each
   * gateway (including embedded gateways). The first argument of is the member ID of the gateway,
   * and the second argument is the gateway container itself.
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
  public SpringClusterBuilder withGatewayConfig(
      final BiConsumer<MemberId, SpringGatewayNode.Builder> modifier) {
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
  public SpringClusterBuilder withGatewayConfig(
      final Consumer<SpringGatewayNode.Builder> modifier) {
    gatewayConfig = (memberId, gateway) -> modifier.accept(gateway);
    return this;
  }

  /**
   * Sets the configuration function that will be executed in the {@link #build()} method on each
   * broker. The first argument is the broker ID, and the second argument is the broker container
   * itself.
   *
   * <p>NOTE: in case of conflicts with {@link #nodeConfig} or {@link #gatewayConfig} this
   * configuration will override them.
   *
   * @param modifier the function that will be applied on all cluster brokers
   * @return this builder instance for chaining
   */
  public SpringClusterBuilder withBrokerConfig(
      final BiConsumer<MemberId, SpringBrokerNode.Builder> modifier) {
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
   * @param modifier the function that will be applied on all cluster brokers
   * @return this builder instance for chaining
   */
  public SpringClusterBuilder withBrokerConfig(final Consumer<SpringBrokerNode.Builder> modifier) {
    brokerConfig = (id, broker) -> modifier.accept(broker);
    return this;
  }

  /**
   * Sets the working directory where all broker data will be stored. If non-null, then under this
   * directory, you will have one folder per broker, e.g.: broker-0, broker-1, broker-2, etc.
   *
   * @param directory the root data directory for this cluster
   * @return this builder instance for chaining
   */
  public SpringClusterBuilder withDirectory(final Path directory) {
    this.directory = directory;
    return this;
  }

  /**
   * Builds a new Zeebe cluster. Will create {@link #brokersCount} brokers (accessible later via
   * {@link SpringCluster#brokers()}) and {@link #gatewaysCount} standalone gateways (accessible
   * later via {@link SpringCluster#gateways()}).
   *
   * <p>If {@link #useEmbeddedGateway} is true, then all brokers will have the embedded gateway
   * enabled and the right topology check configured. Additionally, {@link SpringCluster#gateways()}
   * will also include them, along with any other additional standalone gateway.
   *
   * <p>For standalone gateways, if {@link #brokersCount} is at least one, then a random broker is
   * picked as the contact point for all gateways.
   *
   * @return a new Zeebe cluster
   */
  public SpringCluster build() {
    gateways.clear();
    brokers.clear();

    validate();
    createBrokers();

    // gateways are configured after brokers such that we can set the right contact point if there
    // is one
    createGateways();

    // finalize building all the nodes, freezing configuration
    final var brokerNodes =
        brokers.entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().build()));
    final var gatewayNodes =
        gateways.entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().build()));

    return new SpringCluster(name, replicationFactor, partitionsCount, brokerNodes, gatewayNodes);
  }

  private void applyConfigFunctions(
      final MemberId id, final AbstractSpringBuilder<?, ?, ?> builder) {
    nodeConfig.accept(builder);

    if (builder instanceof final SpringGatewayNode.Builder gateway) {
      gatewayConfig.accept(id, gateway);
    }

    if (builder instanceof final SpringBrokerNode.Builder broker) {
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

    // since initial contact points has to container all known brokers, we can only configure it
    // AFTER the base broker configuration
    final var contactPoints = getInitialContactPoints();
    brokers
        .values()
        .forEach(
            builder ->
                builder.withConfig(cfg -> cfg.getCluster().setInitialContactPoints(contactPoints)));
  }

  private SpringBrokerNode.Builder createBroker(final int index) {
    final var builder =
        new SpringBrokerNode.Builder()
            .withConfig(cfg -> cfg.getNetwork().getCommandApi().setPort(randomPort()))
            .withConfig(cfg -> cfg.getNetwork().getInternalApi().setPort(randomPort()))
            .withConfig(
                cfg -> {
                  final var cluster = cfg.getCluster();
                  cluster.setNodeId(index);
                  cluster.setPartitionsCount(partitionsCount);
                  cluster.setReplicationFactor(replicationFactor);
                  cluster.setClusterSize(brokersCount);
                  cluster.setClusterName(name);
                });

    if (useEmbeddedGateway) {
      builder
          .withConfig(cfg -> cfg.getGateway().setEnable(useEmbeddedGateway))
          .withConfig(cfg -> cfg.getGateway().getNetwork().setPort(randomPort()));
    }

    if (useRecordingExporter) {
      final var exporterConfig = new ExporterCfg();
      exporterConfig.setClassName(RecordingExporter.class.getName());
      builder.withConfig(cfg -> cfg.getExporters().put("recording", exporterConfig));
    }

    if (directory != null) {
      final var workingDirectory = directory.resolve("broker-" + index);
      //noinspection ResultOfMethodCallIgnored
      workingDirectory.toFile().mkdir();
      builder.withWorkingDirectory(workingDirectory);
    }

    return builder;
  }

  private static int randomPort() {
    return SocketUtil.getNextAddress().getPort();
  }

  private void createGateways() {
    for (int i = 0; i < gatewaysCount; i++) {
      final var id = "gateway-" + i;
      final var memberId = MemberId.from(id);
      final var gateway = createGateway(id);

      applyConfigFunctions(memberId, gateway);
      gateways.put(memberId, gateway);
    }
  }

  private SpringGatewayNode.Builder createGateway(final String id) {
    return new SpringGatewayNode.Builder()
        .withConfig(cfg -> cfg.getNetwork().setPort(randomPort()))
        .withConfig(cfg -> cfg.getCluster().setInitialContactPoints(getInitialContactPoints()))
        .withConfig(
            cfg -> {
              final var cluster = cfg.getCluster();
              cluster.setPort(randomPort());
              cluster.setMemberId(id);
              cluster.setClusterName(name);
            });
  }

  private List<String> getInitialContactPoints() {
    return brokers.values().stream()
        .map(builder -> "localhost:" + builder.config().getNetwork().getInternalApi().getPort())
        .toList();
  }
}
