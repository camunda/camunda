/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container.cluster;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.lifecycle.Startables;

public class CamundaCluster implements Startable {
  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaCluster.class);

  private final Network network;
  private final String name;
  private final Map<String, GatewayNode<? extends GenericContainer<?>>> gateways;
  private final Map<Integer, BrokerNode<? extends GenericContainer<?>>> brokers;
  private final int replicationFactor;
  private final int partitionsCount;

  /**
   * Creates a new cluster from the given parameters.
   *
   * @param network the network attached to each node
   * @param name the name of the cluster (should also be configured on the nodes)
   * @param gateways the set of gateway nodes, identified by their member ID
   * @param brokers the set of broker nodes, identified by their node ID
   * @param replicationFactor the replication factor of the cluster
   * @param partitionsCount the number of partitions in the cluster
   */
  public CamundaCluster(
      final Network network,
      final String name,
      final Map<String, GatewayNode<? extends GenericContainer<?>>> gateways,
      final Map<Integer, BrokerNode<? extends GenericContainer<?>>> brokers,
      final int replicationFactor,
      final int partitionsCount) {
    this.network = network;
    this.name = name;
    this.gateways = Collections.unmodifiableMap(gateways);
    this.brokers = Collections.unmodifiableMap(brokers);
    this.replicationFactor = replicationFactor;
    this.partitionsCount = partitionsCount;
  }

  /** Returns a new cluster builder */
  public static CamundaClusterBuilder builder() {
    return new CamundaClusterBuilder();
  }

  /**
   * Starts all containers in the cluster. This is a blocking method: it will return only when all
   * containers are marked as ready.
   *
   * <p>NOTE: although gateways could technically be started in any order, brokers
   * <strong>must</strong> be started in parallel, as they will fail to be ready if they cannot at
   * least form a Raft (during the initial startup).
   */
  @Override
  public void start() {
    // as containers are not thread safe (especially the containerId property), it's important that
    // we don't try to start the same container on different threads (i.e. start brokers, then
    // gateways), as they may end up creating multiple real containers from a single
    // GenericContainer if the containerId property isn't updated in either thread
    LOGGER.info(
        "Starting cluster {} with {} brokers, {} gateways, {} partitions, and a replication factor"
            + " of {}",
        name,
        brokers.size(),
        gateways.size(),
        partitionsCount,
        replicationFactor);
    Startables.deepStart(getClusterContainers()).join();
  }

  /** Stops all containers in the cluster. */
  @Override
  public void stop() {
    // as containers are not thread safe in general, there may be a race condition when stopping
    // them on the default fork join pool if the threads from the pool haven't synchronized with
    // this one or the ones used to start the container. it could be in very rare cases that they
    // see no containerId property and stop wouldn't do anything. at any rate, since it's cheap to
    // stop containers, we can simply do it sequentially
    getClusterContainers().forEach(Startable::stop);
  }

  /** Returns the network over which all containers are communicating */
  public Network getNetwork() {
    return network;
  }

  /** Returns the replication factor configured for the brokers */
  public int getReplicationFactor() {
    return replicationFactor;
  }

  /** Returns the partitions count configured for the brokers */
  public int getPartitionsCount() {
    return partitionsCount;
  }

  /** Returns the cluster name */
  public String getName() {
    return name;
  }

  /**
   * Returns a map of the gateways in the cluster, where the keys are the memberIds, and the values
   * the gateway containers.
   *
   * <p>NOTE: this may include brokers with embedded gateways as well. To check if a node is a
   * standalone gateway or a broker, you can check if it's an instance of {@link
   * io.camunda.container.CamundaContainer.GatewayContainer} or not.
   *
   * @return the gateways in this cluster
   */
  public Map<String, GatewayNode<? extends GenericContainer<?>>> getGateways() {
    return gateways;
  }

  /**
   * Returns a map of the brokers in the cluster, where the keys are the broker's nodeId, and the
   * values the broker containers.
   *
   * @return the brokers in this cluster
   */
  public Map<Integer, BrokerNode<? extends GenericContainer<?>>> getBrokers() {
    return brokers;
  }

  /**
   * Returns a map of all nodes in the cluster, where the keys are the member IDs (for brokers, the
   * node ID), and the values are the containers.
   *
   * @return the nodes of this cluster
   */
  public Map<String, ClusterNode<? extends GenericContainer<?>>> getNodes() {
    final Map<String, ClusterNode<? extends GenericContainer<?>>> nodes = new HashMap<>(gateways);
    brokers.forEach((id, node) -> nodes.put(String.valueOf(id), node));

    return nodes;
  }

  /**
   * Builds a new client builder by picking a random gateway started gateway for it and disabling
   * transport security.
   *
   * @return a new client builder with the gateway and transport security pre-configured
   * @throws NoSuchElementException if there are no started gateways
   */
  public CamundaClientBuilder newClientBuilder() {
    final GatewayNode<?> gateway = getAvailableGateway();

    return CamundaClient.newClientBuilder()
        .grpcAddress(gateway.getGrpcAddress())
        .restAddress(gateway.getRestAddress());
  }

  /**
   * Returns the first gateway which can accept requests from a Zeebe client.
   *
   * @return a gateway ready to accept requests
   * @throws NoSuchElementException if there are no such gateways (e.g. none are started, or they
   *     are dead, etc.)
   */
  public GatewayNode<? extends GenericContainer<?>> getAvailableGateway() {
    return gateways.values().stream()
        .filter(ClusterNode::isStarted)
        .findAny()
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    "Expected at least one gateway for the client to connect to, but there is"
                        + " none"));
  }

  private Stream<? extends GenericContainer<?>> getGatewayContainers() {
    return gateways.values().stream().map(Container::self);
  }

  private Stream<? extends GenericContainer<?>> getBrokerContainers() {
    return brokers.values().stream().map(Container::self);
  }

  private Stream<GenericContainer<? extends GenericContainer<?>>> getClusterContainers() {
    return Stream.concat(getBrokerContainers(), getGatewayContainers()).distinct();
  }

  public static CamundaClientBuilder newClientBuilder(final CamundaCluster cluster) {
    final GatewayNode<?> gateway = cluster.getAvailableGateway();

    return CamundaClient.newClientBuilder()
        .preferRestOverGrpc(false)
        .restAddress(gateway.getRestAddress())
        .grpcAddress(gateway.getGrpcAddress());
  }
}
