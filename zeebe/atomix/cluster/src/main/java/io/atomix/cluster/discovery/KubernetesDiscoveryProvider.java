/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.discovery;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.Node;
import io.atomix.cluster.NodeId;
import io.atomix.utils.event.AbstractListenerManager;
import io.atomix.utils.net.Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kubernetes headless service discovery provider.
 *
 * <p>This discovery provider resolves the DNS name of a Kubernetes headless service to discover the
 * IP addresses of all pods behind the service. It periodically performs DNS lookups to detect
 * changes in the cluster membership as pods are added or removed.
 *
 * <p>The provider uses the standard Kubernetes DNS naming convention:
 * {service-name}.{namespace}.svc.cluster.local
 */
public final class KubernetesDiscoveryProvider
    extends AbstractListenerManager<NodeDiscoveryEvent, NodeDiscoveryEventListener>
    implements NodeDiscoveryProvider {

  public static final Type TYPE = new Type();
  private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesDiscoveryProvider.class);

  private final KubernetesDiscoveryConfig config;
  private final Set<Node> currentNodes = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private ScheduledExecutorService discoveryExecutor;

  public KubernetesDiscoveryProvider(final KubernetesDiscoveryConfig config) {
    this.config = checkNotNull(config, "config cannot be null");
    if (config.getServiceFqdn() == null || config.getServiceFqdn().trim().isEmpty()) {
      throw new IllegalArgumentException("Service FQDN cannot be null or empty");
    }
  }

  /**
   * Creates a new Kubernetes discovery provider builder.
   *
   * @return a new Kubernetes discovery provider builder
   */
  public static KubernetesDiscoveryBuilder builder() {
    return new KubernetesDiscoveryBuilder();
  }

  @Override
  public KubernetesDiscoveryConfig config() {
    return config;
  }

  @Override
  public Set<Node> getNodes() {
    return ImmutableSet.copyOf(currentNodes);
  }

  @Override
  public CompletableFuture<Void> join(final BootstrapService bootstrap, final Node localNode) {
    if (started.compareAndSet(false, true)) {
      LOGGER.info("Starting Kubernetes discovery for service: {}", config.getServiceFqdn());

      discoveryExecutor =
          Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "kubernetes-discovery"));

      // Perform initial discovery
      discoverNodes();

      // Schedule periodic discovery
      discoveryExecutor.scheduleWithFixedDelay(
          this::discoverNodes,
          config.getDiscoveryInterval().toSeconds(),
          config.getDiscoveryInterval().toSeconds(),
          TimeUnit.SECONDS);

      LOGGER.debug("Local node {} joined the Kubernetes discovery service", localNode);
    }

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> leave(final Node localNode) {
    if (started.compareAndSet(true, false)) {
      LOGGER.info("Stopping Kubernetes discovery for service: {}", config.getServiceFqdn());

      if (discoveryExecutor != null) {
        discoveryExecutor.shutdown();
        try {
          if (!discoveryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            discoveryExecutor.shutdownNow();
          }
        } catch (final InterruptedException e) {
          discoveryExecutor.shutdownNow();
          Thread.currentThread().interrupt();
        }
      }

      // Clear current nodes and notify listeners
      final Set<Node> nodesToRemove = ImmutableSet.copyOf(currentNodes);
      currentNodes.clear();

      for (final Node node : nodesToRemove) {
        post(new NodeDiscoveryEvent(NodeDiscoveryEvent.Type.LEAVE, node));
      }

      LOGGER.debug("Local node {} left the Kubernetes discovery service", localNode);
    }

    return CompletableFuture.completedFuture(null);
  }

  private void discoverNodes() {
    try {
      final String serviceFqdn = config.getServiceFqdn();

      LOGGER.trace("Resolving DNS for service: {}", serviceFqdn);

      final InetAddress[] addresses = InetAddress.getAllByName(serviceFqdn);
      final Set<Node> discoveredNodes =
          IntStream.range(0, addresses.length)
              .mapToObj(i -> createNode(addresses[i], i))
              .collect(Collectors.toSet());

      LOGGER.trace("Discovered {} nodes for service {}", discoveredNodes.size(), serviceFqdn);

      // Find nodes that joined
      final Set<Node> joinedNodes =
          discoveredNodes.stream()
              .filter(node -> !currentNodes.contains(node))
              .collect(Collectors.toSet());

      // Find nodes that left
      final Set<Node> leftNodes =
          currentNodes.stream()
              .filter(node -> !discoveredNodes.contains(node))
              .collect(Collectors.toSet());

      // Update current nodes
      currentNodes.removeAll(leftNodes);
      currentNodes.addAll(joinedNodes);

      // Notify listeners of changes
      for (final Node node : joinedNodes) {
        LOGGER.debug("Node joined: {}", node);
        post(new NodeDiscoveryEvent(NodeDiscoveryEvent.Type.JOIN, node));
      }

      for (final Node node : leftNodes) {
        LOGGER.debug("Node left: {}", node);
        post(new NodeDiscoveryEvent(NodeDiscoveryEvent.Type.LEAVE, node));
      }

    } catch (final UnknownHostException e) {
      LOGGER.warn(
          "Failed to resolve DNS for service {}: {}", config.getServiceFqdn(), e.getMessage());
    } catch (final Exception e) {
      LOGGER.error("Error during node discovery", e);
    }
  }

  private Node createNode(final InetAddress address, final int index) {
    final String nodeId =
        String.format("k8s-%s-%d", address.getHostAddress().replace(".", "-"), index);

    return Node.builder()
        .withId(NodeId.from(nodeId))
        .withAddress(Address.from(address.getHostAddress(), config.getPort()))
        .build();
  }

  /** Kubernetes discovery provider type. */
  public static class Type implements NodeDiscoveryProvider.Type<KubernetesDiscoveryConfig> {
    private static final String NAME = "kubernetes";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public NodeDiscoveryProvider newProvider(final KubernetesDiscoveryConfig config) {
      return new KubernetesDiscoveryProvider(config);
    }
  }
}
