/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container;

import static org.rnorth.ducttape.unreliables.Unreliables.retryUntilTrue;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.container.cluster.CamundaPort;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.rnorth.ducttape.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;

/**
 * A {@link org.testcontainers.containers.wait.strategy.WaitStrategy} implementation which waits for
 * the gateway's known topology to be "complete". Complete here means that there are at least {@code
 * brokersCount} brokers in the topology, there are {@code partitionsCount} partitions in the
 * topology, and each partition has the required number of replicas (controlled by {@code
 * replicationFactor}), where one of the replicas is a leader and all others are followers.
 *
 * <p>So given {@code brokersCount} of 5, {@code partitionsCount} of 10, {@code replicationFactor}
 * of 3, it would be complete if:
 *
 * <ul>
 *   <li>There are at least 5 known brokers in the topology (identified by their node ID)
 *   <li>There are at least 10 known partitions in the topology (identified by their partition ID)
 *   <li>For each partition, there is exactly 1 broker which is the LEADER
 *   <li>For each partition, there are at least 2 brokers which are FOLLOWERs
 * </ul>
 */
@SuppressWarnings("UnusedReturnValue")
public class ZeebeTopologyWaitStrategy extends AbstractWaitStrategy {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeTopologyWaitStrategy.class);

  private int brokersCount;
  private int replicationFactor;
  private int partitionsCount;
  private int gatewayPort;
  private Supplier<CamundaClientBuilder> clientBuilderProvider;

  /**
   * Creates a new topology wait strategy for a single broker/replica, which is the Zeebe default as
   * well.
   */
  public ZeebeTopologyWaitStrategy() {
    this(1);
  }

  /**
   * Creates a new topology wait strategy for the given number of brokers and a single replica per
   * partition.
   *
   * @param brokersCount the expected number of brokers
   */
  public ZeebeTopologyWaitStrategy(final int brokersCount) {
    this(brokersCount, 1);
  }

  /**
   * Creates a new topology wait strategy for the given number of brokers and replication factor.
   *
   * @param brokersCount the expected number of brokers
   * @param replicationFactor the expected number of replicas per partitions
   */
  public ZeebeTopologyWaitStrategy(final int brokersCount, final int replicationFactor) {
    this(brokersCount, replicationFactor, 1);
  }

  /**
   * Creates a new topology wait strategy for the given number of brokers, partitions, and the
   * replicas per partition.
   *
   * @param brokersCount the expected number of brokers
   * @param replicationFactor the expected number of replicas per partition
   * @param partitionsCount the expected number of partitions
   */
  public ZeebeTopologyWaitStrategy(
      final int brokersCount, final int replicationFactor, final int partitionsCount) {
    this(brokersCount, replicationFactor, partitionsCount, CamundaPort.GATEWAY_GRPC.getPort());
  }

  /**
   * Creates a new topology wait strategy for the given number of brokers, with an expected
   * replication factor, and a custom gateway port. This should only be used if you have properly
   * overridden the gateway port everywhere, and is only for advanced usage.
   *
   * @param brokersCount the expected number of brokers
   * @param replicationFactor the expected number of replicas per partition
   * @param partitionsCount the expected number of partitions
   * @param gatewayPort the custom gateway port
   */
  public ZeebeTopologyWaitStrategy(
      final int brokersCount,
      final int replicationFactor,
      final int partitionsCount,
      final int gatewayPort) {
    this.brokersCount = brokersCount;
    this.replicationFactor = replicationFactor;
    this.partitionsCount = partitionsCount;
    this.gatewayPort = gatewayPort;

    clientBuilderProvider = CamundaClient::newClientBuilder;
  }

  public static ZeebeTopologyWaitStrategy newDefaultTopologyCheck() {
    return new ZeebeTopologyWaitStrategy().forBrokersCount(1);
  }

  /**
   * Sets a new number of expected brokers. The topology is marked complete only when there are at
   * least as many brokers as the given count, and each partition has at least {@code
   * replicationFactor} replicas, and each partition has a leader.
   *
   * @param brokersCount the new expected brokers count
   * @return this strategy, for chaining
   */
  public ZeebeTopologyWaitStrategy forBrokersCount(final int brokersCount) {
    this.brokersCount = brokersCount;
    return this;
  }

  /**
   * Sets a new number of expected number of replicas per partition. The topology is marked complete
   * only when there are at least as many brokers as {@code brokersCount}, and each partition has at
   * least {@code replicationFactor} replicas, and each partition has a leader.
   *
   * @param replicationFactor the new expected replication factor
   * @return this strategy, for chaining
   */
  public ZeebeTopologyWaitStrategy forReplicationFactor(final int replicationFactor) {
    this.replicationFactor = replicationFactor;
    return this;
  }

  /**
   * Sets a new number of expected partitions count. The topology is marked complete only when there
   * are at least as many brokers as {@code brokersCount}, and each partition has at least {@code
   * replicationFactor} replicas, and each partition has a leader.
   *
   * @param partitionsCount the new expected number of partitions
   * @return this strategy, for chaining
   */
  public ZeebeTopologyWaitStrategy forPartitionsCount(final int partitionsCount) {
    this.partitionsCount = partitionsCount;
    return this;
  }

  /**
   * Sets a custom gateway port, only useful if you have configured the gateway port to be a
   * different one.
   *
   * @param gatewayPort the new gateway port
   * @return this strategy, for chaining
   */
  public ZeebeTopologyWaitStrategy forGatewayPort(final int gatewayPort) {
    this.gatewayPort = gatewayPort;
    return this;
  }

  /**
   * Sets a custom client builder provider; this provider will be given the broker contact point
   * based on the given target. This is mostly useful if you wish to enable TLS/SSL on your gateway,
   * at which point you can configure whatever you want.
   *
   * <p>Caveat: as the default provider applies {@link CamundaClientBuilder#usePlaintext()}, if you
   * still wish to use plaintext, make sure to call it as well in your custom provider.
   *
   * @param clientBuilderProvider the new client builder provider
   * @return this strategy, for chaining
   */
  public ZeebeTopologyWaitStrategy forBuilder(
      final Supplier<CamundaClientBuilder> clientBuilderProvider) {
    this.clientBuilderProvider = clientBuilderProvider;
    return this;
  }

  @Override
  protected void waitUntilReady() {
    final TopologyHolder latestTopology = new TopologyHolder();

    try (final CamundaClient client = newCamundaClient(waitStrategyTarget)) {
      final String containerName = waitStrategyTarget.getContainerInfo().getName();
      LOGGER.info(
          "{}: Waiting for {} for topology to have at least {} brokers, {} partitions with "
              + "{} replicas, and each partition to have a leader",
          containerName,
          startupTimeout,
          brokersCount,
          partitionsCount,
          replicationFactor);

      retryUntilTrue(
          (int) startupTimeout.toMillis(),
          TimeUnit.MILLISECONDS,
          () ->
              getRateLimiter()
                  .getWhenReady(
                      () -> {
                        latestTopology.topology = getTopology(client);
                        LOGGER.trace("{}: Topology: {}", containerName, latestTopology.topology);
                        return isTopologyComplete(latestTopology.topology, containerName);
                      }));
    } catch (final TimeoutException e) {
      throw new ContainerLaunchException(
          String.format(
              "Timed out waiting for gateway topology to be complete; latest known topology: %s",
              latestTopology));
    }
  }

  private CamundaClient newCamundaClient(final WaitStrategyTarget waitStrategyTarget) {
    //noinspection HttpUrlsUsage
    final URI gatewayAddress =
        URI.create(
            "http://"
                + waitStrategyTarget.getHost()
                + ":"
                + waitStrategyTarget.getMappedPort(gatewayPort));

    // The topology wait strategy only knows the gRPC port, so force gRPC for topology requests.
    // Without this, the client defaults to REST (HTTP) which would hit the gRPC port and fail
    // with MalformedInputException when trying to decode binary gRPC framing as UTF-8.
    return clientBuilderProvider
        .get()
        .grpcAddress(gatewayAddress)
        .restAddress(gatewayAddress)
        .preferRestOverGrpc(false)
        .build();
  }

  private boolean isTopologyComplete(final Topology topology, final String containerName) {
    final int actualBrokersCount = topology.getBrokers().size();
    if (actualBrokersCount < brokersCount) {
      return false;
    }

    final Map<Integer, Partition> partitions = buildPartitionsMap(topology);

    if (partitions.size() < partitionsCount) {
      LOGGER.trace(
          "{}: expected {} partitions, but found only {}",
          containerName,
          partitionsCount,
          partitions.size());
      return false;
    }

    for (final Partition partition : partitions.values()) {
      final int leadersCount = partition.leaderIds.size();
      final int followersCount = partition.followerIds.size();
      final int expectedFollowersCount = replicationFactor - 1;
      if (leadersCount != 1) {
        LOGGER.trace(
            "{}: expected exactly one leader, but got {} ({})",
            containerName,
            leadersCount,
            partition.leaderIds);
        return false;
      }

      if (followersCount < expectedFollowersCount) {
        LOGGER.trace(
            "{}: expected at least {} followers, but got {} ({})",
            containerName,
            expectedFollowersCount,
            followersCount,
            partition.followerIds);
        return false;
      }
    }

    return true;
  }

  private Map<Integer, Partition> buildPartitionsMap(final Topology topology) {
    final Map<Integer, Partition> partitions = new HashMap<>();

    for (final BrokerInfo broker : topology.getBrokers()) {
      final int nodeId = broker.getNodeId();

      for (final PartitionInfo partitionInfo : broker.getPartitions()) {
        final int partitionId = partitionInfo.getPartitionId();
        partitions.putIfAbsent(partitionId, new Partition());
        final Partition partition = partitions.get(partitionId);

        if (partitionInfo.isLeader()) {
          partition.leaderIds.add(nodeId);
        } else {
          partition.followerIds.add(nodeId);
        }
      }
    }

    return partitions;
  }

  private Topology getTopology(final CamundaClient client) {
    return client
        .newTopologyRequest()
        .send()
        .join(startupTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  private static final class TopologyHolder {
    private Topology topology;

    @Override
    public String toString() {
      return topology.toString();
    }
  }

  private static final class Partition {
    private final Set<Integer> followerIds = new HashSet<>();
    private final Set<Integer> leaderIds = new HashSet<>();
  }
}
