/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.util.CloseableSilently;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A convenience class representing a one or more Spring applications that form a Zeebe cluster.
 *
 * <p>It's recommended to use the {@link TestClusterBuilder} to build one.
 *
 * <p>As the cluster is not started automatically, the nodes can still be modified/configured
 * beforehand. Be aware however that the replication factor and the partitions count cannot be
 * modified: if you configure different values directly on your brokers, then you may run into
 * issues. Keep in mind as well that the gateways and brokers should be treated as immutable
 * collections.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * final class MyClusteredTest {
 *   private TestCluster cluster;
 *
 *   @BeforeEach
 *   void beforeEach() {
 *     cluster = TestCluster.builder()
 *         .withBrokersCount(3)
 *         .withReplicationFactor(3)
 *         .withPartitionsCount(1)
 *         .useEmbeddedGateway(true)
 *         .build();
 *   }
 *
 *   @AfterEach
 *   void afterEach() {
 *     cluster.stop();
 *   }
 *
 *   @Test
 *   void shouldConnectToCluster() {
 *     // given
 *     cluster.start();
 *     cluster.awaitCompleteTopology();
 *
 *     // when
 *     final Topology topology;
 *     try (final ZeebeClient client = cluster.newClientBuilder().build()) {
 *       topology = c.newTopologyRequest().send().join();
 *     }
 *
 *     // then
 *     assertThat(topology.getClusterSize()).isEqualTo(3);
 *   }
 * }
 * }</pre>
 */
@SuppressWarnings({"ClassCanBeRecord", "UnusedReturnValue"})
public final class TestCluster implements CloseableSilently {
  private static final Logger LOGGER = LoggerFactory.getLogger(TestCluster.class);

  private final String name;
  private final Map<MemberId, TestStandaloneGateway> gateways;
  private final Map<MemberId, TestStandaloneBroker> brokers;
  private final int replicationFactor;
  private int partitionsCount;

  /**
   * Creates a new cluster from the given parameters.
   *
   * @param name the name of the cluster (should also be configured on the nodes)
   * @param replicationFactor the replication factor of the cluster
   * @param partitionsCount the number of partitions in the cluster
   * @param brokers the set of broker nodes, identified by their node ID
   * @param gateways the set of gateway nodes, identified by their member ID
   */
  public TestCluster(
      final String name,
      final int replicationFactor,
      final int partitionsCount,
      final Map<MemberId, TestStandaloneBroker> brokers,
      final Map<MemberId, TestStandaloneGateway> gateways) {
    this.name = name;
    this.replicationFactor = replicationFactor;
    this.partitionsCount = partitionsCount;
    this.brokers = Collections.unmodifiableMap(brokers);
    this.gateways = Collections.unmodifiableMap(gateways);
  }

  /** Returns a new cluster builder */
  public static TestClusterBuilder builder() {
    return new TestClusterBuilder();
  }

  /**
   * Starts all applications in the cluster.
   *
   * <p>NOTE: This is a blocking method which returns when all applications are started from the
   * Spring point of view. This means the broker startup has begun, but is not necessarily yet
   * finished, such that it may not be ready.
   *
   * <p>You can use {@link #await(TestHealthProbe, Duration)} to wait until a specific probe (e.g.
   * {@link TestHealthProbe#READY} succeeds on all nodes, or {@link
   * #awaitCompleteTopology(Duration)} to wait until the topology is complete for all gateways.
   */
  public TestCluster start() {
    LOGGER.info(
        "Starting cluster {} with {} brokers, {} gateways, {} partitions, and a replication factor"
            + " of {}",
        name,
        brokers.size(),
        gateways.size(),
        partitionsCount,
        replicationFactor);

    final var started =
        nodes().values().stream()
            .map(node -> CompletableFuture.runAsync(node::start))
            .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(started).join();
    return this;
  }

  /**
   * Stops all containers in the cluster.
   *
   * <p>NOTE: this method is blocking, and returns when all nodes are completely shut down.
   */
  public TestCluster shutdown() {
    final var stopped =
        nodes().values().stream()
            .map(node -> CompletableFuture.runAsync(node::stop))
            .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(stopped).join();
    return this;
  }

  /**
   * Returns the replication factor configured for the brokers; also used to check for a complete
   * topology.
   */
  public int replicationFactor() {
    return replicationFactor;
  }

  /**
   * Returns the partition count configured for the brokers; also used to check for a complete
   * topology.
   */
  public int partitionsCount() {
    return partitionsCount;
  }

  /** Returns the cluster name. */
  public String name() {
    return name;
  }

  /**
   * Returns the first gateway which can accept requests from a Zeebe client.
   *
   * <p>NOTE: this includes brokers with embedded gateways.
   *
   * @return a gateway ready to accept requests
   * @throws NoSuchElementException if there are no such gateways (e.g. none are started, or they
   *     are dead, etc.)
   */
  public TestGateway<?> availableGateway() {
    return allGateways()
        .filter(this::isReady)
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("No available gateway for cluster"));
  }

  /**
   * Returns the first gateway which is running. The gateway may not be ready to accept requests.
   *
   * <p>NOTE: this includes brokers with embedded gateways.
   *
   * @return a gateway
   * @throws NoSuchElementException if there are no such gateways (e.g. none are started, or they
   *     are dead, etc.)
   */
  public TestGateway<?> anyGateway() {
    return allGateways()
        .filter(TestApplication::isStarted)
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("No available gateway for cluster"));
  }

  /**
   * Returns a map of the gateways in the cluster, where the keys are the memberIds, and the values
   * the gateway containers.
   *
   * <p>NOTE: this does not include brokers which are embedded gateways.
   *
   * @return the standalone gateways in this cluster
   */
  public Map<MemberId, TestStandaloneGateway> gateways() {
    return gateways;
  }

  /**
   * Returns a map of the brokers in the cluster, where the keys are the broker's nodeId, and the
   * values the broker containers.
   *
   * @return the brokers in this cluster
   */
  public Map<MemberId, TestStandaloneBroker> brokers() {
    return brokers;
  }

  /**
   * Returns a map of all nodes in the cluster, where the keys are the member IDs (for brokers, the
   * node ID), and the values are the nodes.
   *
   * @return the nodes of this cluster
   */
  public Map<MemberId, TestApplication<?>> nodes() {
    final Map<MemberId, TestApplication<?>> nodes = new HashMap<>(brokers);
    nodes.putAll(gateways);

    return nodes;
  }

  /**
   * Builds a new client builder by picking a random gateway started gateway for it and disabling
   * transport security.
   *
   * <p>NOTE: this will properly automatically configure the security level, but will not configure
   * authentication.
   *
   * @return a new client builder with the gateway and transport security pre-configured
   * @throws NoSuchElementException if there are no started gateways
   */
  @SuppressWarnings("resource")
  public ZeebeClientBuilder newClientBuilder() {
    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .grpcAddress(availableGateway().grpcAddress());
  }

  /**
   * Convenience alias for {@link #awaitCompleteTopology(Duration)} with a default value of 1 minute
   * per brokers.
   */
  @SuppressWarnings("resource")
  public TestCluster awaitCompleteTopology() {
    awaitCompleteTopology(Duration.ofMinutes(brokers.size()));
    return this;
  }

  /**
   * Waits until every gateway in the cluster sees a complete and healthy topology, as defined by
   * the {@link #replicationFactor}, {@link #partitionsCount}, and count of {@link #brokers} in this
   * cluster.
   *
   * @param timeout maximum time to block before failing
   * @throws org.awaitility.core.ConditionTimeoutException if timeout expires before the topology is
   *     complete
   */
  public TestCluster awaitCompleteTopology(final Duration timeout) {
    return awaitCompleteTopology(brokers.size(), partitionsCount, replicationFactor, timeout);
  }

  /**
   * Waits until every gateway in the cluster sees a complete and healthy topology, as defined by
   * the {@link #replicationFactor}, {@link #partitionsCount}, and count of {@link #brokers} in this
   * cluster.
   *
   * @param clusterSize the expected brokers count
   * @param partitionsCount the expected partitions count
   * @param replicationFactor the expected number of replicas per partition
   * @param timeout maximum time to block before failing
   * @throws org.awaitility.core.ConditionTimeoutException if timeout expires before the topology is
   *     complete
   */
  public TestCluster awaitCompleteTopology(
      final int clusterSize,
      final int partitionsCount,
      final int replicationFactor,
      final Duration timeout) {
    Awaitility.await("until cluster topology is complete")
        .atMost(timeout)
        .untilAsserted(
            () ->
                assertThat(allGateways())
                    .allSatisfy(
                        gateway ->
                            assertCompleteTopology(
                                gateway, clusterSize, partitionsCount, replicationFactor)));
    return this;
  }

  public TestCluster awaitHealthyTopology() {
    awaitHealthyTopology(Duration.ofMinutes(brokers.size()));
    return this;
  }

  public TestCluster awaitHealthyTopology(final Duration timeout) {
    Awaitility.await("until cluster topology is complete")
        .atMost(timeout)
        .untilAsserted(() -> assertThat(allGateways()).allSatisfy(this::assertHealthyTopology));
    return this;
  }

  /**
   * Convenience method for {@link #await(TestHealthProbe, Duration)} with a default timeout of 1
   * minute per node in the cluster.
   *
   * @param probe the probe to wait for
   */
  @SuppressWarnings("resource")
  public TestCluster await(final TestHealthProbe probe) {
    await(probe, Duration.ofMinutes(brokers.size() + gateways.size()));
    return this;
  }

  /**
   * Waits until the given probe succeeds, or until the timeout expires.
   *
   * @param probe the probe to check
   * @param timeout maximum time to block
   */
  public TestCluster await(final TestHealthProbe probe, final Duration timeout) {
    final var nodes = nodes().values();
    Awaitility.await("until '%s' probe succeeds on all nodes".formatted(probe))
        .atMost(timeout)
        .untilAsserted(() -> assertThat(nodes).allSatisfy(node -> assertProbe(node, probe)));
    return this;
  }

  @Override
  public void close() {
    //noinspection resource
    shutdown();
  }

  private boolean isReady(final TestApplication<?> node) {
    if (!node.isStarted()) {
      return false;
    }

    try {
      node.probe(TestHealthProbe.READY);
    } catch (final Exception e) {
      LOGGER.trace("Node {} is not ready", node.nodeId(), e);
      return false;
    }

    return true;
  }

  private void assertCompleteTopology(
      final TestGateway<?> node,
      final int clusterSize,
      final int partitionsCount,
      final int replicationFactor) {
    assertThatCode(() -> node.probe(TestHealthProbe.READY))
        .as("gateway '%s' is ready", node.nodeId())
        .doesNotThrowAnyException();
    try (final var client = node.newClientBuilder().build()) {
      TopologyAssert.assertThat(client.newTopologyRequest().send().join())
          .isComplete(clusterSize, partitionsCount, replicationFactor);
    }
  }

  private void assertHealthyTopology(final TestGateway<?> node) {
    assertThatCode(() -> node.probe(TestHealthProbe.READY))
        .as("gateway '%s' is ready", node.nodeId())
        .doesNotThrowAnyException();
    try (final var client = node.newClientBuilder().build()) {
      TopologyAssert.assertThat(client.newTopologyRequest().send().join()).isHealthy();
    }
  }

  private void assertProbe(final TestApplication<?> node, final TestHealthProbe probe) {
    assertThatCode(() -> node.probe(probe)).doesNotThrowAnyException();
  }

  private Stream<TestGateway<?>> allGateways() {
    return nodes().values().stream()
        .filter(TestApplication::isGateway)
        .filter(TestGateway.class::isInstance)
        .map(node -> (TestGateway<?>) node);
  }

  public void setPartitionCount(final int count) {
    partitionsCount = count;
    brokers.forEach((id, b) -> b.brokerConfig().getCluster().setPartitionsCount(partitionsCount));
  }
}
