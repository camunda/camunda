/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container.cluster;

import io.camunda.container.CamundaContainer;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.container.CamundaContainer.GatewayContainer;
import io.camunda.container.ZeebeTopologyWaitStrategy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * Convenience class to help build Camunda clusters.
 *
 * <p>The default configuration of this cluster is equivalent to starting a single {@link
 * CamundaContainer} with all defaults, that is:
 *
 * <ul>
 *   <li>one broker in the cluster with embedded gateway
 *   <li>one partition
 *   <li>replication factor of 1
 * </ul>
 *
 * By default, all nodes will use the {@link Network#SHARED} network as well.
 *
 * <p>Example usage for a cluster with 3 brokers, 3 partitions, replication factor 3, and all
 * embedded gateways:
 *
 * <pre>{@code
 * final CamundaCluster cluster = CamundaCluster.builder()
 *    .withEmbeddedGateway(true)
 *    .withGatewaysCount(0)
 *    .withBrokersCount(3)
 *    .withReplicationFactor(3)
 *    .withPartitionsCount(3)
 *    .build();
 *
 * }</pre>
 *
 * <p>Example for the same as the above, but this time with one standalone gateway instead of
 * embedded gateways:
 *
 * <pre>{@code
 * final CamundaCluster cluster = CamundaCluster.builder()
 *    .withEmbeddedGateway(false)
 *    .withGatewaysCount(1)
 *    .withBrokersCount(3)
 *    .withReplicationFactor(3)
 *    .withPartitionsCount(3)
 *    .build();
 *
 * }</pre>
 *
 * <p>Example for the same as the above, but with a mix of gateways:
 *
 * <pre>{@code
 * final CamundaCluster cluster = CamundaCluster.builder()
 *    .withEmbeddedGateway()
 *    .withGatewaysCount(2)
 *    .withBrokersCount(3)
 *    .withReplicationFactor(3)
 *    .withPartitionsCount(3)
 *    .build();
 *
 * }</pre>
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class CamundaClusterBuilder {

  public static final String DEFAULT_CLUSTER_NAME = "camunda-cluster";
  private static final String CAMUNDA_NETWORK_ALIAS_PREFIX = "camunda-";
  private static final String GATEWAY_NETWORK_ALIAS_PREFIX = "camunda-gateway-";
  private Network network = Network.SHARED;
  private String name = DEFAULT_CLUSTER_NAME;

  private int gatewaysCount = 0;

  private int brokersCount = 1;
  private int partitionsCount = 1;
  private int replicationFactor = 1;
  private boolean useEmbeddedGateway = true;
  private DockerImageName gatewayImageName = CamundaContainer.getBrokerImageName();
  private DockerImageName brokerImageName = CamundaContainer.getBrokerImageName();

  private Consumer<ClusterNode<?>> nodeConfig = cfg -> {};
  private BiConsumer<Integer, BrokerNode<?>> brokerConfig = (id, cfg) -> {};
  private BiConsumer<String, GatewayNode<?>> gatewayConfig = (memberId, cfg) -> {};

  private final Map<String, GatewayNode<?>> gateways = new HashMap<>();
  private final Map<Integer, BrokerNode<?>> brokers = new HashMap<>();

  /**
   * If true, the brokers created by this cluster will use embedded gateways. By default, this is
   * true.
   *
   * @param useEmbeddedGateway true or false to enable the embedded gateway on the brokers
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withEmbeddedGateway(final boolean useEmbeddedGateway) {
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
  public CamundaClusterBuilder withGatewaysCount(final int gatewaysCount) {
    this.gatewaysCount = gatewaysCount;
    return this;
  }

  /**
   * The number of brokers to create in this cluster. By default, this is 1. Any gateway part of
   * this cluster will use this number as well for its {@link
   * ZeebeTopologyWaitStrategy#forBrokersCount(int)}.
   *
   * <p>Note that it's possible to create a cluster with no brokers, as this is maybe a valid set-up
   * for testing purposes. If that's the case, the gateways will not wait for the topology to be
   * complete (as they cannot know the topology), and will not be configured with a contact point.
   *
   * <p>NOTE: setting this to 0 will also set the replication factor and partitions count to 0.
   *
   * @param brokersCount the number of brokers to create
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withBrokersCount(final int brokersCount) {
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
   * partition. Any gateway in the cluster will also use this number for its {@link
   * ZeebeTopologyWaitStrategy#forPartitionsCount(int)}.
   *
   * <p>Note that the number of partitions must be greater than or equal to 1! If you do not want to
   * have any brokers, then set {@link #withBrokersCount(int)} to 0 instead.
   *
   * @param partitionsCount the number of partitions to distribute across the cluster
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withPartitionsCount(final int partitionsCount) {
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
  public CamundaClusterBuilder withReplicationFactor(final int replicationFactor) {
    if (replicationFactor <= 0) {
      throw new IllegalArgumentException(
          "Expected replicationFactor to be at least 1, but was " + replicationFactor);
    }

    this.replicationFactor = replicationFactor;
    return this;
  }

  /**
   * Sets the network the containers should use to communicate between each other. It's recommended
   * to create a new network if you have more than one or two nodes, or if you're running multiple
   * tests in parallel.
   *
   * <p>By default, will use {@link Network#SHARED}.
   *
   * @param network the network containers will use to communicate between each other
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withNetwork(final Network network) {
    this.network = Objects.requireNonNull(network);
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
  public CamundaClusterBuilder withName(final String name) {
    if (name == null || name.trim().length() < 3) {
      throw new IllegalArgumentException(
          "Expected cluster name to be at least 3 characters, but was " + name);
    }

    this.name = name;
    return this;
  }

  /**
   * Sets the docker image for separate gateways. This could be used to create a test against
   * specific Zeebe version.
   *
   * @param gatewayImageName the docker image name of the Zeebe
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withGatewayImage(final DockerImageName gatewayImageName) {
    this.gatewayImageName = Objects.requireNonNull(gatewayImageName);
    return this;
  }

  /**
   * Sets the docker image for brokers with embedded gateways. This could be used to create a test
   * against specific Zeebe version.
   *
   * @param brokerImageName the docker image name of the Zeebe
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withBrokerImage(final DockerImageName brokerImageName) {
    this.brokerImageName = Objects.requireNonNull(brokerImageName);
    return this;
  }

  /**
   * Sets the docker image for brokers with embedded gateways and separate gateways. This could be
   * used to create a test against specific Zeebe version.
   *
   * @param imageName the docker image name of the Zeebe
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withImage(final DockerImageName imageName) {
    return withGatewayImage(imageName).withBrokerImage(imageName);
  }

  /**
   * Sets the configuration function that will be executed in the {@link #build()} method on both
   * brokers and gateways (embedded gateways included). NOTE: this configuration has the lowest
   * priority, e.g. other configurations ({@link #gatewayConfig} or {@link #brokerConfig}) will
   * override this configuration in case of conflicts.
   *
   * @param nodeCfgFunction the function that will be applied on all cluster nodes
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withNodeConfig(final Consumer<ClusterNode<?>> nodeCfgFunction) {
    nodeConfig = nodeCfgFunction;
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
   * @param gatewayCfgFunction the function that will be applied on all cluster gateways (embedded
   *     ones included)
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withGatewayConfig(
      final BiConsumer<String, GatewayNode<?>> gatewayCfgFunction) {
    gatewayConfig = gatewayCfgFunction;
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
   * @param gatewayCfgFunction the function that will be applied on all cluster gateways (embedded
   *     ones included)
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withGatewayConfig(
      final Consumer<GatewayNode<?>> gatewayCfgFunction) {
    gatewayConfig = (memberId, gateway) -> gatewayCfgFunction.accept(gateway);
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
   * @param brokerCfgFunction the function that will be applied on all cluster brokers
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withBrokerConfig(
      final BiConsumer<Integer, BrokerNode<?>> brokerCfgFunction) {
    brokerConfig = brokerCfgFunction;
    return this;
  }

  /**
   * Sets the configuration function that will be executed in the {@link #build()} method on each
   * broker.
   *
   * <p>NOTE: in case of conflicts with {@link #nodeConfig} or {@link #gatewayConfig} this
   * configuration will override them.
   *
   * @param brokerCfgFunction the function that will be applied on all cluster brokers
   * @return this builder instance for chaining
   */
  public CamundaClusterBuilder withBrokerConfig(final Consumer<BrokerNode<?>> brokerCfgFunction) {
    brokerConfig = (id, broker) -> brokerCfgFunction.accept(broker);
    return this;
  }

  /**
   * Builds a new Zeebe cluster. Will create {@link #brokersCount} brokers (accessible later via
   * {@link CamundaCluster#getBrokers()}) and {@link #gatewaysCount} standalone gateways (accessible
   * later via {@link CamundaCluster#getGateways()}).
   *
   * <p>If {@link #useEmbeddedGateway} is true, then all brokers will have the embedded gateway
   * enabled and the right topology check configured. Additionally, {@link
   * CamundaCluster#getGateways()} will also include them, along with any other additional
   * standalone gateway.
   *
   * <p>NOTE: as a rule of thumb, we had one minute to the startup timeout for each node in the
   * cluster. For example, if you have 2 gateways and 3 brokers, each container will have a maximum
   * startup time of 5 minutes. You can still change that by configuring the containers manually
   * after building but before starting the cluster.
   *
   * <p>For standalone gateways, if {@link #brokersCount} is at least one, then a random broker is
   * picked as the contact point for all gateways.
   *
   * @return a new Zeebe cluster
   */
  public CamundaCluster build() {
    gateways.clear();
    brokers.clear();

    validate();
    createBrokers();

    // gateways are configured after brokers such that we can set the right contact point if there
    // is one
    createStandaloneGateways();

    // apply free-form configuration functions
    brokers.forEach(this::applyConfigFunctions);
    gateways.entrySet().stream()
        .filter(gw -> !(gw.getValue() instanceof BrokerNode<?>))
        .forEach(gw -> applyConfigFunctions(gw.getKey(), gw.getValue()));

    return new CamundaCluster(network, name, gateways, brokers, replicationFactor, partitionsCount);
  }

  private void applyConfigFunctions(final Object id, final ClusterNode<?> node) {
    nodeConfig.accept(node);

    // Standalone gateways
    if (node instanceof GatewayNode && !(node instanceof BrokerNode)) {
      gatewayConfig.accept(String.valueOf(id), (GatewayNode<?>) node);
    }

    if (node instanceof BrokerNode) {
      if (gateways.containsKey(String.valueOf(id))) {
        // this is a broker with embedded gateway
        gatewayConfig.accept(String.valueOf(id), (GatewayNode<?>) node);
      }
      // Broker config is allowed to override gateway config
      brokerConfig.accept((Integer) id, (BrokerNode<?>) node);
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
      final BrokerNode<?> broker;

      if (useEmbeddedGateway) {
        final BrokerContainer container = new BrokerContainer(brokerImageName);
        container.withEmbeddedGateway();
        configureGateway(container);

        broker = container;
        gateways.put(String.valueOf(i), container);
      } else {
        broker = new BrokerContainer(brokerImageName);
      }

      configureBroker(broker, i);
      brokers.put(i, broker);
    }

    // since initial contact points has to container all known brokers, we can only configure it
    // AFTER the base broker configuration
    configureBrokerInitialContactPoints();
  }

  private void createStandaloneGateways() {
    final ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < gatewaysCount; i++) {
      final String memberId = GATEWAY_NETWORK_ALIAS_PREFIX + i;
      //noinspection resource
      final GatewayContainer gateway = createStandaloneGateway(memberId);
      gateway.withStartupTimeout(Duration.ofMinutes((long) gatewaysCount + brokersCount));

      if (brokersCount > 0) {
        final BrokerNode<?> contactPoint = brokers.get(random.nextInt(0, brokers.size()));
        gateway
            .dependsOn(contactPoint.self())
            .withUnifiedConfig(
                cfg ->
                    cfg.getCluster()
                        .setInitialContactPoints(
                            List.of(contactPoint.getInternalClusterAddress())));
      }
    }
  }

  private GatewayContainer createStandaloneGateway(final String memberId) {
    final GatewayContainer gateway = new GatewayContainer(gatewayImageName);

    gateway
        .withNetwork(network)
        .withNetworkAliases(memberId)
        .withProperty("zeebe.gateway.cluster.cluster-name", name)
        .withProperty("zeebe.gateway.cluster.host", gateway.getInternalHost())
        .withProperty("zeebe.gateway.cluster.member-id", memberId)
        .withUnifiedConfig(
            cfg -> {
              cfg.getCluster().setName(name);
              cfg.getCluster().setGatewayId(memberId);
            });

    configureGateway(gateway);
    gateways.put(memberId, gateway);
    return gateway;
  }

  private void configureGateway(final GatewayNode<?> gateway) {
    gateway.withTopologyCheck(
        new ZeebeTopologyWaitStrategy()
            .forBrokersCount(brokersCount)
            .forPartitionsCount(partitionsCount)
            .forReplicationFactor(replicationFactor));
  }

  private void configureBroker(final BrokerNode<?> broker, final int index) {
    final String hostName = CAMUNDA_NETWORK_ALIAS_PREFIX + index;

    broker
        .withNetwork(network)
        .withNetworkAliases(hostName)
        .withUnifiedConfig(
            cfg -> {
              cfg.getCluster().setName(name);
              cfg.getCluster().setPartitionCount(partitionsCount);
              cfg.getCluster().setReplicationFactor(replicationFactor);
              cfg.getCluster().setSize(brokersCount);
              cfg.getCluster().setNodeId(index);
              cfg.getCluster().getNetwork().setAdvertisedHost(broker.getInternalHost());
            })
        .withStartupTimeout(Duration.ofMinutes((long) brokersCount + gatewaysCount));
  }

  private void configureBrokerInitialContactPoints() {
    final var initialContactPoints =
        brokers.values().stream().map(BrokerNode::getInternalClusterAddress).toList();
    brokers
        .values()
        .forEach(
            b ->
                b.withUnifiedConfig(
                    cfg -> cfg.getCluster().setInitialContactPoints(initialContactPoints)));
  }
}
